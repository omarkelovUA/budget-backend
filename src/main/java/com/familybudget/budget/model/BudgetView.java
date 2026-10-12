package com.familybudget.budget.model;

import java.math.BigDecimal;
import java.util.UUID;

import com.familybudget.category.model.CategoryView;
import com.familybudget.common.model.Visibility;

public record BudgetView(UUID id, CategoryView category, String period, BigDecimal plannedAmount,
		BigDecimal factAmount, Visibility visibility) {

}
