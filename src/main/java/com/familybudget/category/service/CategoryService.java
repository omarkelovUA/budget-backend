package com.familybudget.category.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.category.entity.CategoryEntity;
import com.familybudget.category.event.CategoryHiding;
import com.familybudget.category.event.CategoryMerged;
import com.familybudget.category.model.AddedCategoryView;
import com.familybudget.category.model.CategoryRequest;
import com.familybudget.category.model.CategoryType;
import com.familybudget.category.model.CategoryView;
import com.familybudget.category.repository.CategoryRepository;
import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.IdConflictException;
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.common.model.AccessScope;
import com.familybudget.common.model.OwnerType;
import com.familybudget.common.model.SyncChanges;
import com.familybudget.common.model.SyncWindow;
import com.familybudget.common.model.Visibility;
import com.familybudget.common.service.SyncTombstones;
import com.familybudget.common.service.UserOrder;
import com.familybudget.family.service.FamilyService;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

/**
 * Public API of the category module; every query is scoped per ARCHITECTURE.md §6.3.
 * Family categories are shared by every member; only the admin changes, deletes or hides
 * them. A member never sees two categories with the same type and name: their personal
 * one is merged into the family's (ARCHITECTURE.md §3.1).
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

	private final CategoryRepository categories;

	private final FamilyService familyService;

	private final UserService userService;

	private final ApplicationEventPublisher events;

	private final UserOrder userOrder;

	private final SyncTombstones tombstones;

	private static final UserOrder.Table POSITIONS = new UserOrder.Table("category_positions", "category_id");

	CategoryService(CategoryRepository categories, FamilyService familyService, UserService userService,
			ApplicationEventPublisher events, UserOrder userOrder, SyncTombstones tombstones) {
		this.categories = categories;
		this.familyService = familyService;
		this.userService = userService;
		this.events = events;
		this.userOrder = userOrder;
		this.tombstones = tombstones;
	}

	/** In the user's own order; new ones last, by type and name. */
	public List<CategoryView> list(UUID userId) {
		return this.userOrder.sort(POSITIONS, userId, visible(userId), CategoryEntity::getId)
			.stream()
			.map(CategoryEntity::toView)
			.toList();
	}

	/**
	 * Sync feed (ARCHITECTURE.md §6.5): the user's and their family's categories written in
	 * {@code window}. One moved out of their reach (hidden by the admin, merged) is deleted.
	 */
	public SyncChanges<CategoryView> changes(UUID userId, SyncWindow window) {
		AccessScope scope = this.familyService.scopeFor(userId);
		List<UUID> ids = this.categories.findChangedIds(scope.userId(), scope.familyId(), window.since(),
				window.until(), window.limit());
		List<UUID> tombstones = this.tombstones.deleted("category", ownerIds(scope), window);
		if (SyncChanges.exceeds(window, ids.size(), tombstones.size())) {
			return SyncChanges.tooMany();
		}
		List<CategoryView> changed = this.categories.findByIdIn(ids)
			.stream()
			.filter((category) -> scope.canAccess(category.getOwnerType(), category.getOwnerId()))
			.map(CategoryEntity::toView)
			.toList();
		Set<UUID> changedIds = changed.stream().map(CategoryView::id).collect(Collectors.toSet());
		return new SyncChanges<>(changed, tombstones.stream().filter((id) -> !changedIds.contains(id)).toList());
	}

	/** Sync feed: the ids {@link #list} returns, in the user's order. */
	public List<UUID> order(UUID userId) {
		return list(userId).stream().map(CategoryView::id).toList();
	}

	/** The user's own order of the categories they see. */
	@Transactional
	public void reorder(UUID userId, List<UUID> ids) {
		this.userOrder.replace(POSITIONS, userId, ids, visible(userId).stream().map(CategoryEntity::getId).toList());
	}

	private List<CategoryEntity> visible(UUID userId) {
		AccessScope scope = this.familyService.scopeFor(userId);
		return this.categories.findVisible(scope.userId(), scope.familyId());
	}

	/** A family category made by a member other than the admin is live at once; the admin is told. */
	@Transactional
	public CategoryView create(UUID userId, CategoryRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		if (request.id() != null) {
			this.tombstones.requireNotDeleted("category", request.id());
			Optional<CategoryEntity> existing = this.categories.findById(request.id());
			if (existing.isPresent()) {
				return replay(scope, existing.get());
			}
		}
		requireUnique(scope, request.type(), request.name(), null);
		CategoryEntity category = new CategoryEntity(AccessScope.ownerTypeFor(request.visibility()),
				scope.ownerIdFor(request.visibility()), request.type());
		category.useClientId(request.id());
		category.apply(request);
		if (request.visibility() == Visibility.SHARED) {
			category.shareWith(scope.ownerIdFor(Visibility.SHARED), addedBy(userId));
		}
		return this.categories.save(category).toView();
	}

	@Transactional
	public CategoryView update(UUID userId, UUID id, CategoryRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		CategoryEntity category = accessible(scope, id);
		requireManageable(userId, category);
		if (Visibility.of(category.getOwnerType()) != request.visibility()) {
			throw new ApiException(HttpStatus.CONFLICT, "visibility-immutable",
					"A category can't be moved between private and shared");
		}
		// Flipping income/expense would silently change what past transactions did to balances.
		if (category.getType() != request.type()) {
			throw new ApiException(HttpStatus.CONFLICT, "category-type-immutable",
					"A category can't change between income and expense");
		}
		requireUnique(scope, request.type(), request.name(), id);
		category.apply(request);
		if (category.getOwnerType() == OwnerType.FAMILY) {
			// A member may have a personal category with the new name.
			mergeDuplicates(category.getOwnerId());
		}
		return category.toView();
	}

	@Transactional
	public void delete(UUID userId, UUID id) {
		CategoryEntity category = accessible(this.familyService.scopeFor(userId), id);
		requireManageable(userId, category);
		try {
			this.categories.delete(category);
			this.categories.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "category-in-use",
					"Delete this category's transactions and budgets first");
		}
	}

	/**
	 * Makes the caller's personal category a family one (the family can't have one with the
	 * same name: the caller would see both). Other members' namesakes merge into it.
	 */
	@Transactional
	public CategoryView share(UUID userId, UUID id) {
		AccessScope scope = this.familyService.scopeFor(userId);
		CategoryEntity category = accessible(scope, id);
		if (category.getOwnerType() == OwnerType.FAMILY) {
			return category.toView();
		}
		UUID familyId = scope.ownerIdFor(Visibility.SHARED);
		category.shareWith(familyId, addedBy(userId));
		this.categories.flush();
		mergeDuplicates(familyId);
		return category.toView();
	}

	/**
	 * The admin takes a family category back as their personal one; refused while anyone
	 * else has data in it (CategoryHiding).
	 */
	@Transactional
	public CategoryView hide(UUID userId, UUID id) {
		UUID familyId = this.familyService.requireAdminOf(userId);
		CategoryEntity category = this.categories.findById(id)
			.filter((c) -> c.getOwnerType() == OwnerType.FAMILY && c.getOwnerId().equals(familyId))
			.orElseThrow(() -> new NotFoundException("Category not found"));
		this.events.publishEvent(new CategoryHiding(id, userId));
		category.hideTo(userId);
		return category.toView();
	}

	/** Family categories members other than the admin added since the admin last looked. */
	public List<AddedCategoryView> addedByMembers(UUID userId) {
		List<CategoryEntity> added = familyCategories(this.familyService.requireAdminOf(userId)).stream()
			.filter((category) -> category.getAddedBy() != null)
			.toList();
		Map<UUID, UserView> users = this.userService
			.getAll(added.stream().map(CategoryEntity::getAddedBy).map(Objects::requireNonNull).toList())
			.stream()
			.collect(Collectors.toMap(UserView::id, Function.identity()));
		return added.stream().map((category) -> {
			UserView user = users.get(category.getAddedBy());
			return new AddedCategoryView(category.toView(), (user != null) ? user.displayName() : "");
		}).toList();
	}

	@Transactional
	public void markAddedSeen(UUID userId) {
		familyCategories(this.familyService.requireAdminOf(userId)).forEach(CategoryEntity::markSeen);
	}

	/**
	 * Folds every member's personal category into the family category with the same type
	 * and name: run when someone joins, a category becomes a family one, or one is renamed.
	 */
	@Transactional
	void mergeDuplicates(UUID familyId) {
		List<CategoryEntity> family = familyCategories(familyId);
		for (CategoryEntity personal : this.categories.findByOwnerTypeAndOwnerIdIn(OwnerType.USER,
				this.familyService.memberIds(familyId))) {
			family.stream()
				.filter((into) -> into.sameAs(personal.getType(), personal.getName()))
				.findFirst()
				.ifPresent((into) -> merge(personal, into));
		}
	}

	/** A category the scope may use, for recording a payment or setting a budget. */
	public CategoryView requireAccessible(AccessScope scope, UUID id) {
		return accessible(scope, id).toView();
	}

	/**
	 * The user's private expense category with this name, created if missing. Used for
	 * bookings the system makes on the user's behalf (e.g. family-transfer write-offs).
	 */
	@Transactional
	public CategoryView systemExpenseCategory(UUID userId, String name) {
		return this.categories
			.findFirstByOwnerTypeAndOwnerIdAndTypeAndName(OwnerType.USER, userId, CategoryType.EXPENSE, name)
			.orElseGet(() -> {
				CategoryEntity category = new CategoryEntity(OwnerType.USER, userId, CategoryType.EXPENSE);
				category.apply(new CategoryRequest(null, CategoryType.EXPENSE, name, "assets/family.svg", "gray",
						Visibility.PRIVATE));
				return this.categories.save(category);
			})
			.toView();
	}

	/**
	 * Views for payments the user may see: someone else's personal category comes back as
	 * {@link CategoryView#unknown}, so its name stays private.
	 */
	public Map<UUID, CategoryView> viewsFor(UUID userId, Collection<UUID> ids) {
		AccessScope scope = this.familyService.scopeFor(userId);
		return this.categories.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(CategoryEntity::getId,
					(category) -> scope.canAccess(category.getOwnerType(), category.getOwnerId()) ? category.toView()
							: CategoryView.unknown(category.getId(), category.getType()),
					(a, b) -> a));
	}

	/** Views regardless of visibility: callers only use them for data the scope already sees. */
	public Map<UUID, CategoryView> views(Collection<UUID> ids) {
		return this.categories.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::toView, (a, b) -> a));
	}

	private static List<UUID> ownerIds(AccessScope scope) {
		return (scope.familyId() != null) ? List.of(scope.userId(), scope.familyId()) : List.of(scope.userId());
	}

	/** A create sent again with the same client id returns what the first one made. */
	private static CategoryView replay(AccessScope scope, CategoryEntity category) {
		if (!scope.canAccess(category.getOwnerType(), category.getOwnerId())) {
			throw new IdConflictException();
		}
		return category.toView();
	}

	private void merge(CategoryEntity from, CategoryEntity into) {
		this.events.publishEvent(new CategoryMerged(from.getId(), into.getId()));
		this.categories.delete(from);
		this.categories.flush();
	}

	private List<CategoryEntity> familyCategories(UUID familyId) {
		return this.categories.findByOwnerTypeAndOwnerIdIn(OwnerType.FAMILY, List.of(familyId));
	}

	/** Who to tell the admin about: a member other than the admin. */
	private @Nullable UUID addedBy(UUID userId) {
		return this.familyService.isAdmin(userId) ? null : userId;
	}

	/** Only the admin changes or deletes a family category; members just use it. */
	private void requireManageable(UUID userId, CategoryEntity category) {
		if (category.getOwnerType() == OwnerType.FAMILY) {
			this.familyService.requireAdminOf(userId);
		}
	}

	/** A user never sees two categories with the same type and name. */
	private void requireUnique(AccessScope scope, CategoryType type, String name, @Nullable UUID excludeId) {
		boolean taken = this.categories.findVisible(scope.userId(), scope.familyId())
			.stream()
			.anyMatch((category) -> category.sameAs(type, name) && !category.getId().equals(excludeId));
		if (taken) {
			throw new ApiException(HttpStatus.CONFLICT, "category-exists",
					"There's already a category with this name and type");
		}
	}

	private CategoryEntity accessible(AccessScope scope, UUID id) {
		return this.categories.findById(id)
			.filter((category) -> scope.canAccess(category.getOwnerType(), category.getOwnerId()))
			.orElseThrow(() -> new NotFoundException("Category not found"));
	}

}
