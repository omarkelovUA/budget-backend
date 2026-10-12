package com.familybudget.common.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
