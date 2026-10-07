package com.familybudget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import com.familybudget.account.AccountRef;
import com.familybudget.category.CategoryView;

/**
 * Payments carry {@code account} + {@code category}, transfers {@code fromAccount} +
 * {@code toAccount}; the unused pair is omitted, matching the frontend's union type.
 * {@code editable} says whether the caller may delete it, so clients don't repeat the rule.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionView(UUID id, TransactionKind kind, BigDecimal amount, LocalDate date, @Nullable String note,
		UUID recordedBy, @Nullable AccountRef account, @Nullable CategoryView category,
		@Nullable AccountRef fromAccount, @Nullable AccountRef toAccount, boolean editable) {

}
