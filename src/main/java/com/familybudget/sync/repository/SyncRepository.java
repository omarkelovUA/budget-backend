package com.familybudget.sync.repository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.familybudget.common.model.SyncWindow;

/** The sync horizon and the per-user markers the V22 triggers write. */
@Repository
public class SyncRepository {

	private final JdbcTemplate jdbc;

	SyncRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * The oldest transaction still running. Every transaction below it has finished, so
	 * no row stamped below it can show up later: it's a safe cursor.
	 */
	public long horizon() {
		String horizon = this.jdbc.queryForObject("select pg_snapshot_xmin(pg_current_snapshot())::text",
				String.class);
		return Long.parseLong(horizon);
	}

	/** Bumped after a restore from a dump, where transaction ids start over (V23). */
	public int generation() {
		Integer generation = this.jdbc.queryForObject("select generation from sync_generation", Integer.class);
		return (generation != null) ? generation : 0;
	}

	/** The kinds of the user's markers in the window: RESET, ACCOUNT_ORDER, CATEGORY_ORDER. */
	public Set<String> markers(UUID userId, SyncWindow window) {
		return new HashSet<>(this.jdbc.queryForList(
				"select distinct kind from sync_markers where user_id = ?"
						+ " and sync_txid >= ?::text::xid8 and sync_txid < ?::text::xid8",
				String.class, userId, window.since(), window.until()));
	}

}
