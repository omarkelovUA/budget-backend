package com.familybudget.sync.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import com.familybudget.account.model.AccountView;
import com.familybudget.budget.model.BudgetView;
import com.familybudget.category.model.CategoryView;
import com.familybudget.family.model.FamilyView;
import com.familybudget.transaction.model.TransactionView;
import com.familybudget.user.model.UserSettingsView;

/**
 * One answer of the sync feed (ARCHITECTURE.md §6.5). With {@code resetRequired} the
 * client downloads everything through the list endpoints and continues from
 * {@code cursor}; otherwise it applies the changes. {@code settings}, {@code family} and
 * the orders are only sent when they changed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncChangesView(String cursor, boolean resetRequired, List<AccountView> accounts,
		List<CategoryView> categories, List<TransactionView> transactions, List<BudgetView> budgets,
		Deleted deleted, @Nullable UserSettingsView settings, @Nullable FamilyView family,
		@Nullable List<UUID> accountOrder, @Nullable List<UUID> categoryOrder) {

	public static SyncChangesView reset(long cursor) {
		return new SyncChangesView(Long.toString(cursor), true, List.of(), List.of(), List.of(), List.of(),
				new Deleted(List.of(), List.of(), List.of(), List.of()), null, null, null, null);
	}

	public record Deleted(List<UUID> accounts, List<UUID> categories, List<UUID> transactions, List<UUID> budgets) {
	}

}
