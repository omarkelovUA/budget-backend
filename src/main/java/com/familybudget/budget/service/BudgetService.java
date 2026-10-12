package com.familybudget.budget.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.common.model.AccessScope;
import com.familybudget.common.model.OwnerType;
import com.familybudget.common.model.Visibility;
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

	BudgetService(BudgetRepository budgets, CategoryService categoryService, TransactionService transactionService,
			FamilyService familyService) {
		this.budgets = budgets;
		this.categoryService = categoryService;
		this.transactionService = transactionService;
		this.familyService = familyService;
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

	@Transactional
	public BudgetView create(UUID userId, BudgetRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		BudgetEntity budget = new BudgetEntity(AccessScope.ownerTypeFor(request.visibility()),
				scope.ownerIdFor(request.visibility()));
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
