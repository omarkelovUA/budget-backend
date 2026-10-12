package com.familybudget.budget.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.budget.repository.BudgetRepository;
import com.familybudget.family.event.FamilyDeleted;

/** A deleted family's budgets go to its last member, like its categories. */
@Component
public class FamilyBudgetHandover {

	private final BudgetRepository budgets;

	FamilyBudgetHandover(BudgetRepository budgets) {
		this.budgets = budgets;
	}

	@EventListener
	@Transactional
	void on(FamilyDeleted event) {
		this.budgets.transferFamilyBudgets(event.familyId(), event.lastMemberId());
	}

}
