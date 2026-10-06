package com.familybudget.budget;

import java.math.BigDecimal;
import java.util.UUID;

import com.familybudget.category.CategoryView;
import com.familybudget.common.Visibility;

public record BudgetView(UUID id, CategoryView category, String period, BigDecimal plannedAmount,
		BigDecimal factAmount, Visibility visibility) {

}
