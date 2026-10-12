package com.familybudget.common.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.NotFoundException;

/**
 * A user's own order of the accounts or categories they see (PLAN.md M24). Each module
 * keeps it in its own table of (user_id, item, position); this reads, replaces and applies
 * it.
 */
@Component
public class UserOrder {

	/** A module's positions table and its item column (constants, never user input). */
	public record Table(String name, String itemColumn) {
	}

	private final JdbcTemplate jdbc;

	UserOrder(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The items with a position first, by position; the rest after, in the order given. */
	public <T> List<T> sort(Table table, UUID userId, List<T> items, Function<T, UUID> id) {
		Map<UUID, Integer> positions = new HashMap<>();
		this.jdbc.query("select " + table.itemColumn() + ", position from " + table.name() + " where user_id = ?",
				(row) -> {
					positions.put(row.getObject(1, UUID.class), row.getInt(2));
				}, userId);
		List<T> sorted = new ArrayList<>(items);
		// List.sort is stable, so unpositioned items keep their default order.
		sorted.sort(Comparator.comparing((item) -> positions.getOrDefault(id.apply(item), Integer.MAX_VALUE)));
		return sorted;
	}

	/**
	 * Replaces the user's order with {@code ids}, which must be distinct and among
	 * {@code visible} ({@code 404 not-found} otherwise, like any item the user can't see).
	 */
	public void replace(Table table, UUID userId, List<UUID> ids, Collection<UUID> visible) {
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validation-failed", "Each item can appear only once");
		}
		if (!new HashSet<>(visible).containsAll(ids)) {
			throw new NotFoundException("Not found");
		}
		this.jdbc.update("delete from " + table.name() + " where user_id = ?", userId);
		List<Object[]> rows = new ArrayList<>();
		for (int position = 0; position < ids.size(); position++) {
			rows.add(new Object[] { userId, ids.get(position), position });
		}
		this.jdbc.batchUpdate("insert into " + table.name() + " (user_id, " + table.itemColumn() + ", position)"
				+ " values (?, ?, ?)", rows);
	}

}
