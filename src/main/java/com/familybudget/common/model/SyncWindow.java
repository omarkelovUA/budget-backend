package com.familybudget.common.model;

/**
 * Which changes a sync feed call covers (ARCHITECTURE.md §6.5): rows whose
 * {@code sync_txid} (the id of the transaction that last wrote them) is in
 * {@code [since, until)}. {@code limit} caps how many changed rows a module loads; a
 * window with more than that is answered with a full download instead.
 */
public record SyncWindow(long since, long until, int limit) {

}
