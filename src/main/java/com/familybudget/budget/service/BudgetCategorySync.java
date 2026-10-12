package com.familybudget.budget.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.budget.repository.BudgetRepository;
import com.familybudget.category.event.CategoryHiding;
import com.familybudget.category.event.CategoryMerged;

/** Keeps budgets on the right category when categories merge, and guards hiding one. */
@Component
public class BudgetCategorySync {

	private final BudgetRepository budgets;

	BudgetCategorySync(BudgetRepository budgets) {
		this.budgets = budgets;
	}

	@EventListener
	@Transactional
	void on(CategoryMerged event) {
		this.budgets.moveCategory(event.from(), event.into());
		this.budgets.deleteByCategory(event.from());
	}

	/** A family budget, or someone else's own one, would lose its category. */
	@EventListener
	void on(CategoryHiding event) {
		if (this.budgets.existsOthersOnCategory(event.categoryId(), event.adminId())) {
			throw CategoryHiding.usedByFamily();
		}
	}

}
