package com.familybudget.account;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.familybudget.common.Visibility;

/** Create and full update (PUT). Balance is editable to allow manual corrections. */
record AccountRequest(@NotBlank @Size(max = 100) String name, @NotNull @Digits(integer = 17, fraction = 2) BigDecimal balance,
		@NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String currency,
		@NotBlank @Size(max = 255) String icon, @NotBlank @Size(max = 50) String color, @NotNull Visibility visibility) {

}
