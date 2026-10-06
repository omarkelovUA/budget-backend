package com.familybudget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A payment sets {@code accountId} + {@code categoryId}; a transfer sets
 * {@code fromAccountId} + {@code toAccountId}.
 */
record TransactionRequest(@NotNull TransactionKind kind,
		@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount, @NotNull LocalDate date,
		@Nullable @Size(max = 500) String note, @Nullable UUID accountId, @Nullable UUID categoryId,
		@Nullable UUID fromAccountId, @Nullable UUID toAccountId) {

}
