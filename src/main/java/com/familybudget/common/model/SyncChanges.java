package com.familybudget.common.model;

import java.util.List;
import java.util.UUID;

/**
 * One module's part of the sync feed: rows to upsert and ids to drop on the client.
 * {@code overflow}: more changes than the window allowed, so none were built.
 */
public record SyncChanges<T>(List<T> changed, List<UUID> deleted, boolean overflow) {

	public SyncChanges(List<T> changed, List<UUID> deleted) {
		this(changed, deleted, false);
	}

	public static <T> SyncChanges<T> tooMany() {
		return new SyncChanges<>(List.of(), List.of(), true);
	}

	/** Whether {@code changed} candidates plus {@code deleted} ids leave no room in the window. */
	public static boolean exceeds(SyncWindow window, int changed, int deleted) {
		return changed + deleted >= window.limit();
	}

	public int size() {
		return this.changed.size() + this.deleted.size();
	}

}
