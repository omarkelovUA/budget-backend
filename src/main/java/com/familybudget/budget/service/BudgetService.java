package com.familybudget.budget.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.budget.entity.BudgetEntity;
import com.familybudget.budget.model.BudgetRequest;
import com.familybudget.budget.model.BudgetView;
import com.familybudget.budget.repository.BudgetRepository;
import com.familybudget.category.model.CategoryView;
import com.familybudget.category.service.CategoryService;
import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.IdConflictException;
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.common.model.AccessScope;
import com.familybudget.common.model.OwnerType;
import com.familybudget.common.model.SyncChanges;
import com.familybudget.common.model.SyncWindow;
import com.familybudget.common.model.Visibility;
import com.familybudget.common.service.SyncTombstones;
import com.familybudget.family.service.FamilyService;
import com.familybudget.transaction.service.TransactionService;

/**
 * Public API of the budget module. {@code factAmount} counts the category's payments in
 * the budget's month: a private budget only the owner's own payments, a shared budget
 * every family member's.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {

	private final BudgetRepository budgets;

	private final CategoryService categoryService;

	private final TransactionService transactionService;

	private final FamilyService familyService;

	private final SyncTombstones tombstones;

	BudgetService(BudgetRepository budgets, CategoryService categoryService, TransactionService transactionService,
			FamilyService familyService, SyncTombstones tombstones) {
		this.budgets = budgets;
		this.categoryService = categoryService;
		this.transactionService = transactionService;
		this.familyService = familyService;
		this.tombstones = tombstones;
	}

	public List<BudgetView> list(UUID userId, @Nullable YearMonth period) {
		AccessScope scope = this.familyService.scopeFor(userId);
		List<BudgetEntity> visible = this.budgets.findVisible(scope.userId(), scope.familyId())
			.stream()
			.filter((budget) -> period == null || budget.getPeriod().equals(period))
			.toList();
		Map<UUID, CategoryView> categories = this.categoryService
			.views(visible.stream().map(BudgetEntity::getCategoryId).toList());
		return visible.stream().map((budget) -> toView(budget, categories.get(budget.getCategoryId()))).toList();
	}

	/**
	 * Sync feed (ARCHITECTURE.md §6.5): the user's and their family's budgets written in
	 * {@code window}. {@code factAmount} is as of now; other members' payments don't resend it.
	 */
	public SyncChanges<BudgetView> changes(UUID userId, SyncWindow window) {
		AccessScope scope = this.familyService.scopeFor(userId);
		List<UUID> ids = this.budgets.findChangedIds(scope.userId(), scope.familyId(), window.since(), window.until(),
				window.limit());
		List<UUID> ownerIds = (scope.familyId() != null) ? List.of(scope.userId(), scope.familyId())
				: List.of(scope.userId());
		List<UUID> tombstones = this.tombstones.deleted("budget", ownerIds, window);
		if (SyncChanges.exceeds(window, ids.size(), tombstones.size())) {
			return SyncChanges.tooMany();
		}
		List<BudgetEntity> changed = this.budgets.findByIdIn(ids)
			.stream()
			.filter((budget) -> scope.canAccess(budget.getOwnerType(), budget.getOwnerId()))
			.toList();
		Map<UUID, CategoryView> categories = this.categoryService
			.views(changed.stream().map(BudgetEntity::getCategoryId).toList());
		Set<UUID> changedIds = changed.stream().map(BudgetEntity::getId).collect(Collectors.toSet());
		List<UUID> deleted = tombstones.stream().filter((id) -> !changedIds.contains(id)).toList();
		return new SyncChanges<>(
				changed.stream().map((budget) -> toView(budget, categories.get(budget.getCategoryId()))).toList(),
				deleted);
	}

	@Transactional
	public BudgetView create(UUID userId, BudgetRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		if (request.id() != null) {
			this.tombstones.requireNotDeleted("budget", request.id());
			Optional<BudgetEntity> existing = this.budgets.findById(request.id());
			if (existing.isPresent()) {
				return replay(scope, existing.get());
			}
		}
		BudgetEntity budget = new BudgetEntity(AccessScope.ownerTypeFor(request.visibility()),
				scope.ownerIdFor(request.visibility()));
		budget.useClientId(request.id());
		return save(budget, scope, request);
	}

	@Transactional
	public BudgetView update(UUID userId, UUID id, BudgetRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		BudgetEntity budget = accessible(scope, id);
		if (Visibility.of(budget.getOwnerType()) != request.visibility()) {
			throw new ApiException(HttpStatus.CONFLICT, "visibility-immutable",
					"A budget can't be moved between private and shared");
		}
		return save(budget, scope, request);
	}

	@Transactional
	public void delete(UUID userId, UUID id) {
		this.budgets.delete(accessible(this.familyService.scopeFor(userId), id));
	}

	private BudgetView save(BudgetEntity budget, AccessScope scope, BudgetRequest request) {
		CategoryView category = this.categoryService.requireAccessible(scope, request.categoryId());
		// Family members couldn't see the category behind a shared budget on a private one.
		if (request.visibility() == Visibility.SHARED && category.visibility() != Visibility.SHARED) {
			throw new ApiException(HttpStatus.CONFLICT, "category-not-shared",
					"A shared budget needs a shared category");
		}
		budget.apply(category.id(), YearMonth.parse(request.period()), request.plannedAmount());
		try {
			return toView(this.budgets.saveAndFlush(budget), category);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "budget-exists",
					"There is already a budget for this category and month");
		}
	}

	/** A create sent again with the same client id returns what the first one made. */
	private BudgetView replay(AccessScope scope, BudgetEntity budget) {
		if (!scope.canAccess(budget.getOwnerType(), budget.getOwnerId())) {
			throw new IdConflictException();
		}
		return toView(budget, this.categoryService.views(List.of(budget.getCategoryId())).get(budget.getCategoryId()));
	}

	private BudgetEntity accessible(AccessScope scope, UUID id) {
		return this.budgets.findById(id)
			.filter((budget) -> scope.canAccess(budget.getOwnerType(), budget.getOwnerId()))
			.orElseThrow(() -> new NotFoundException("Budget not found"));
	}

	private BudgetView toView(BudgetEntity budget, CategoryView category) {
		UUID recordedBy = (budget.getOwnerType() == OwnerType.USER) ? budget.getOwnerId() : null;
		BigDecimal fact = this.transactionService.sumPayments(budget.getCategoryId(), budget.getPeriod(), recordedBy);
		return new BudgetView(budget.getId(), category, budget.getPeriod().toString(), budget.getPlannedAmount(), fact,
				Visibility.of(budget.getOwnerType()));
	}

}
