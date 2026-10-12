package com.familybudget.common.model;

import java.util.List;
import java.util.UUID;

/** One module's part of the sync feed: rows to upsert and ids to drop on the client. */
public record SyncChanges<T>(List<T> changed, List<UUID> deleted) {

	public int size() {
		return this.changed.size() + this.deleted.size();
	}

}
