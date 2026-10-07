package com.familybudget.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Create and full update (PUT), owner only. Balance is editable to allow manual
 * corrections. {@code grants} replaces the whole list; members left out are hidden, and a
 * missing list means none.
 */
record AccountRequest(@NotBlank @Size(max = 100) String name, @NotNull @Digits(integer = 17, fraction = 2) BigDecimal balance,
		@NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String currency,
		@NotBlank @Size(max = 255) String icon, @NotBlank @Size(max = 50) String color,
		@Nullable @Size(max = 20) List<@Valid @NotNull GrantRequest> grants) {

	record GrantRequest(@NotNull UUID userId, @NotNull GrantLevel level) {
	}

}
