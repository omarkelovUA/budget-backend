package com.familybudget.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V22 change tracking for mobile sync (ARCHITECTURE §6.5): runs the migrations up to V21 on its
 * own database, inserts a family with an account, a category and a payment, then applies
 * V22.
 */
@Testcontainers
class SyncMigrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	private static final UUID ALICE = UUID.randomUUID();

	private static final UUID FAMILY = UUID.randomUUID();

	private static final UUID ACCOUNT = UUID.randomUUID();

	private static final UUID CATEGORY = UUID.randomUUID();

	private static final UUID PAYMENT = UUID.randomUUID();

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrateWithExistingData() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "21").migrate();

		user(ALICE);
		jdbc.update("INSERT INTO families (id, name, created_by, created_at) VALUES (?, 'F', ?, now())", FAMILY, ALICE);
		jdbc.update("INSERT INTO family_members (user_id, family_id, role, joined_at) VALUES (?, ?, 'ADMIN', now())",
				ALICE, FAMILY);
		jdbc.update("INSERT INTO accounts (id, owner_user_id, name, balance, currency, icon, color, created_at)"
				+ " VALUES (?, ?, 'Card', 100, 'UAH', 'i', 'c', now())", ACCOUNT, ALICE);
		jdbc.update("INSERT INTO categories (id, owner_type, owner_id, type, name, icon, color, created_at)"
				+ " VALUES (?, 'FAMILY', ?, 'EXPENSE', 'Food', 'i', 'c', now())", CATEGORY, FAMILY);
		jdbc.update("INSERT INTO transactions (id, kind, amount, occurred_on, recorded_by, account_id, category_id,"
				+ " created_at) VALUES (?, 'PAYMENT', 10, current_date, ?, ?, ?, now())", PAYMENT, ALICE, ACCOUNT,
				CATEGORY);

		flyway(dataSource, "latest").migrate();
	}

	@ParameterizedTest
	@ValueSource(strings = { "accounts", "categories", "transactions", "budgets", "users", "families" })
	void existingRowsAreStamped(String table) {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE sync_txid IS NULL", Long.class))
			.isZero();
	}

	@Test
	void updatesRestamp() {
		long before = txid("accounts", ACCOUNT);

		jdbc.update("UPDATE accounts SET balance = balance + 1 WHERE id = ?", ACCOUNT);

		assertThat(txid("accounts", ACCOUNT)).isGreaterThan(before);
	}

	@Test
	void deletesLeaveScopedTombstones() {
		jdbc.update("DELETE FROM transactions WHERE id = ?", PAYMENT);

		assertThat(jdbc.queryForObject("SELECT entity FROM sync_tombstones WHERE id = ?", String.class, PAYMENT))
			.isEqualTo("transaction");
		assertThat(scope(PAYMENT)).containsExactly(ACCOUNT);
	}

	@Test
	void ownerChangeLeavesATombstoneForTheOldOwner() {
		UUID hidden = UUID.randomUUID();
		jdbc.update("INSERT INTO categories (id, owner_type, owner_id, type, name, icon, color, created_at)"
				+ " VALUES (?, 'FAMILY', ?, 'EXPENSE', 'Hidden', 'i', 'c', now())", hidden, FAMILY);

		jdbc.update("UPDATE categories SET owner_type = 'USER', owner_id = ? WHERE id = ?", ALICE, hidden);

		assertThat(scope(hidden)).containsExactly(FAMILY);
	}

	@Test
	void membershipChangesResetEveryMember() {
		UUID bob = UUID.randomUUID();
		user(bob);

		jdbc.update("INSERT INTO family_members (user_id, family_id, role, joined_at) VALUES (?, ?, 'MEMBER', now())",
				bob, FAMILY);

		assertThat(markers(ALICE, "RESET")).isPositive();
		assertThat(markers(bob, "RESET")).isPositive();
	}

	@Test
	void grantChangesResetTheGrantee() {
		UUID carol = UUID.randomUUID();
		user(carol);
		jdbc.update("INSERT INTO account_access (account_id, user_id, level) VALUES (?, ?, 'VISIBLE')", ACCOUNT, carol);
		assertThat(markers(carol, "RESET")).isEqualTo(1);

		jdbc.update("UPDATE account_access SET level = 'VISIBLE' WHERE account_id = ? AND user_id = ?", ACCOUNT, carol);

		assertThat(markers(carol, "RESET")).isEqualTo(1);
	}

	@Test
	void reorderMarksTheOrder() {
		jdbc.update("INSERT INTO account_positions (user_id, account_id, position) VALUES (?, ?, 0)", ALICE, ACCOUNT);

		assertThat(markers(ALICE, "ACCOUNT_ORDER")).isPositive();
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
	}

	private static void user(UUID id) {
		jdbc.update("INSERT INTO users (id, email, display_name, used_currencies, created_at) VALUES (?, ?, 'U', '{UAH}', now())", id,
				id + "@example.com");
	}

	private static long txid(String table, UUID id) {
		return Long.parseLong(
				jdbc.queryForObject("SELECT sync_txid::text FROM " + table + " WHERE id = ?", String.class, id));
	}

	private static List<UUID> scope(UUID id) {
		return jdbc.queryForObject("SELECT scope_ids FROM sync_tombstones WHERE id = ?",
				(rs, row) -> List.of((UUID[]) rs.getArray(1).getArray()), id);
	}

	private static long markers(UUID userId, String kind) {
		return jdbc.queryForObject("SELECT count(*) FROM sync_markers WHERE user_id = ? AND kind = ?", Long.class,
				userId, kind);
	}

}
