package com.familybudget.category;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.AccessScope;
import com.familybudget.common.ApiException;
import com.familybudget.common.NotFoundException;
import com.familybudget.common.Visibility;
import com.familybudget.family.FamilyService;

/** Public API of the category module; every query is scoped per ARCHITECTURE.md §6.3. */
@Service
@Transactional(readOnly = true)
public class CategoryService {

	private final CategoryRepository categories;

	private final FamilyService familyService;

	CategoryService(CategoryRepository categories, FamilyService familyService) {
		this.categories = categories;
		this.familyService = familyService;
	}

	public List<CategoryView> list(UUID userId) {
		AccessScope scope = this.familyService.scopeFor(userId);
		return this.categories.findVisible(scope.userId(), scope.familyId())
			.stream()
			.map(CategoryEntity::toView)
			.toList();
	}

	@Transactional
	CategoryView create(UUID userId, CategoryRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		CategoryEntity category = new CategoryEntity(AccessScope.ownerTypeFor(request.visibility()),
				scope.ownerIdFor(request.visibility()), request.type());
		category.apply(request);
		return this.categories.save(category).toView();
	}

	@Transactional
	CategoryView update(UUID userId, UUID id, CategoryRequest request) {
		CategoryEntity category = accessible(this.familyService.scopeFor(userId), id);
		if (Visibility.of(category.getOwnerType()) != request.visibility()) {
			throw new ApiException(HttpStatus.CONFLICT, "visibility-immutable",
					"A category can't be moved between private and shared");
		}
		// Flipping income/expense would silently change what past transactions did to balances.
		if (category.getType() != request.type()) {
			throw new ApiException(HttpStatus.CONFLICT, "category-type-immutable",
					"A category can't change between income and expense");
		}
		category.apply(request);
		return category.toView();
	}

	@Transactional
	void delete(UUID userId, UUID id) {
		CategoryEntity category = accessible(this.familyService.scopeFor(userId), id);
		try {
			this.categories.delete(category);
			this.categories.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "category-in-use",
					"Delete this category's transactions and budgets first");
		}
	}

	/** A category the scope may use, for recording a payment or setting a budget. */
	public CategoryView requireAccessible(AccessScope scope, UUID id) {
		return accessible(scope, id).toView();
	}

	/** Views regardless of visibility: callers only use them for data the scope already sees. */
	public Map<UUID, CategoryView> views(Collection<UUID> ids) {
		return this.categories.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::toView, (a, b) -> a));
	}

	private CategoryEntity accessible(AccessScope scope, UUID id) {
		return this.categories.findById(id)
			.filter((category) -> scope.canAccess(category.getOwnerType(), category.getOwnerId()))
			.orElseThrow(() -> new NotFoundException("Category not found"));
	}

}
