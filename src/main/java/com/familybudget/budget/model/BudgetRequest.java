package com.familybudget.budget.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import com.familybudget.common.model.Visibility;

/** Create and full update (PUT); visibility can't change after creation. */
public record BudgetRequest(@NotNull UUID categoryId,
		@NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be YYYY-MM") String period,
		@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal plannedAmount,
		@NotNull Visibility visibility) {

}
