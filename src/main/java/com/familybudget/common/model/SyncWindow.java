package com.familybudget.common.model;

/**
 * Which changes a sync feed call covers (ARCHITECTURE.md §6.5): rows whose
 * {@code sync_txid} (the id of the transaction that last wrote them) is in
 * {@code [since, until)}. {@code limit} is how many changes the module may still return;
 * reaching it means the client should download everything instead.
 */
public record SyncWindow(long since, long until, int limit) {

	public SyncWindow withLimit(int limit) {
		return new SyncWindow(this.since, this.until, limit);
	}

}
