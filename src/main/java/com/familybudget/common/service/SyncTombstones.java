package com.familybudget.common.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.familybudget.common.exception.ApiException;
import com.familybudget.common.model.SyncWindow;

/**
 * Rows deleted within a sync window (ARCHITECTURE.md §6.5). Triggers write them to
 * {@code sync_tombstones} with the ids of whose data it was; a caller only gets those
 * overlapping its own scope (its user id, family id or accounts).
 */
@Component
public class SyncTombstones {

	private final JdbcTemplate jdbc;

	SyncTombstones(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * For creates with a client id: a retry that arrives after the row was deleted must not
	 * bring it back (and move a balance again). {@code 410 already-deleted} tells the client
	 * to drop it.
	 */
	public void requireNotDeleted(String entity, UUID id) {
		Boolean deleted = this.jdbc.queryForObject(
				"select exists (select 1 from sync_tombstones where entity = ? and id = ?)", Boolean.class, entity, id);
		if (Boolean.TRUE.equals(deleted)) {
			throw new ApiException(HttpStatus.GONE, "already-deleted", "This was deleted in the meantime");
		}
	}

	/** {@code entity} is the trigger's name for the table: account, category, transaction or budget. */
	public List<UUID> deleted(String entity, Collection<UUID> scopeIds, SyncWindow window) {
		if (scopeIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.queryForList(
				"select distinct id from sync_tombstones where entity = ? and scope_ids && ?::uuid[]"
						+ " and sync_txid >= ?::text::xid8 and sync_txid < ?::text::xid8 limit ?",
				UUID.class, entity, scopeIds.toArray(UUID[]::new), window.since(), window.until(), window.limit());
	}

}
