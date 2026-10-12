package com.familybudget.sync.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.account.model.AccountView;
import com.familybudget.account.service.AccountService;
import com.familybudget.budget.model.BudgetView;
import com.familybudget.budget.service.BudgetService;
import com.familybudget.category.model.CategoryView;
import com.familybudget.category.service.CategoryService;
import com.familybudget.common.exception.ApiException;
import com.familybudget.common.model.SyncChanges;
import com.familybudget.common.model.SyncWindow;
import com.familybudget.family.service.FamilyService;
import com.familybudget.sync.model.SyncChangesView;
import com.familybudget.sync.repository.SyncRepository;
import com.familybudget.transaction.model.TransactionView;
import com.familybudget.transaction.service.TransactionService;
import com.familybudget.user.service.UserService;

/**
 * Gathers what changed for a user between their cursor and the current horizon
 * (ARCHITECTURE.md §6.5). Anything the feed can't express cheaply (a first sync, changed
 * access, a very long time offline) is answered with {@code resetRequired}.
 */
@Service
@Transactional(readOnly = true)
public class SyncService {

	static final int MAX_CHANGES = 1000;

	private final SyncRepository sync;

	private final AccountService accountService;

	private final CategoryService categoryService;

	private final TransactionService transactionService;

	private final BudgetService budgetService;

	private final UserService userService;

	private final FamilyService familyService;

	SyncService(SyncRepository sync, AccountService accountService, CategoryService categoryService,
			TransactionService transactionService, BudgetService budgetService, UserService userService,
			FamilyService familyService) {
		this.sync = sync;
		this.accountService = accountService;
		this.categoryService = categoryService;
		this.transactionService = transactionService;
		this.budgetService = budgetService;
		this.userService = userService;
		this.familyService = familyService;
	}

	public SyncChangesView changes(UUID userId, @Nullable String since) {
		long until = this.sync.horizon();
		if (since == null) {
			return SyncChangesView.reset(until);
		}
		long from = parse(since);
		if (from > until) {
			return SyncChangesView.reset(until);
		}
		SyncWindow window = new SyncWindow(from, until, MAX_CHANGES + 1);
		Set<String> markers = this.sync.markers(userId, window);
		if (markers.contains("RESET")) {
			return SyncChangesView.reset(until);
		}
		SyncChanges<AccountView> accounts = this.accountService.changes(userId, window);
		SyncChanges<CategoryView> categories = this.categoryService.changes(userId, window);
		SyncChanges<TransactionView> transactions = this.transactionService.changes(userId, window);
		SyncChanges<BudgetView> budgets = this.budgetService.changes(userId, window);
		if (accounts.size() + categories.size() + transactions.size() + budgets.size() > MAX_CHANGES) {
			return SyncChangesView.reset(until);
		}
		return new SyncChangesView(Long.toString(until), false, accounts.changed(), categories.changed(),
				transactions.changed(), budgets.changed(), new SyncChangesView.Deleted(accounts.deleted(),
						categories.deleted(), transactions.deleted(), budgets.deleted()),
				this.userService.anyChangedIn(List.of(userId), window) ? this.userService.settings(userId) : null,
				this.familyService.changedIn(userId, window) ? this.familyService.current(userId) : null,
				markers.contains("ACCOUNT_ORDER") ? this.accountService.order(userId) : null,
				markers.contains("CATEGORY_ORDER") ? this.categoryService.order(userId) : null);
	}

	private static long parse(String cursor) {
		try {
			long value = Long.parseLong(cursor);
			if (value >= 0) {
				return value;
			}
		}
		catch (NumberFormatException ex) {
			// Falls through to the problem below.
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-sync-cursor", "Sync cursor is not valid");
	}

}
