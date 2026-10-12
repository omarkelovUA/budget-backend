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
import com.familybudget.common.exception.ApiException;
import com.familybudget.common.model.SyncChanges;
import com.familybudget.common.model.SyncWindow;
import com.familybudget.sync.model.SyncChangesView;
import com.familybudget.sync.repository.SyncRepository;

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

	SyncService(SyncRepository sync, AccountService accountService) {
		this.sync = sync;
		this.accountService = accountService;
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
		if (accounts.size() > MAX_CHANGES) {
			return SyncChangesView.reset(until);
		}
		return new SyncChangesView(Long.toString(until), false, accounts.changed(), List.of(), List.of(), List.of(),
				new SyncChangesView.Deleted(accounts.deleted(), List.of(), List.of(), List.of()), null, null, null,
				null);
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
