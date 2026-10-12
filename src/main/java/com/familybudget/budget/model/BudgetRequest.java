package com.familybudget.budget.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.Nullable;

import com.familybudget.common.model.Visibility;

/** Create and full update (PUT); visibility can't change after creation.
 * {@code id}: the client's id for a create (mobile sync); ignored by updates.
 */
public record BudgetRequest(@Nullable UUID id, @NotNull UUID categoryId,
		@NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be YYYY-MM") String period,
		@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal plannedAmount,
		@NotNull Visibility visibility) {

}
