package com.familybudget.transaction.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Replaces a payment with one payment per part. {@code id}: the client's id for the part
 * (mobile sync), as in {@link TransactionRequest}.
 */
public record SplitRequest(@NotNull @Size(min = 2, max = 50) List<@NotNull @Valid Part> parts) {

	public record Part(@Nullable UUID id, @NotNull UUID categoryId,
			@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {

	}

}
