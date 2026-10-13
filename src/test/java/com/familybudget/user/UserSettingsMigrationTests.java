package com.familybudget.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V15 backfill (ARCHITECTURE §3.2): runs the migrations up to V14 on its own database, inserts
 * users with accounts, then applies V15.
 */
@Testcontainers
class UserSettingsMigrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	private static final UUID ALICE = UUID.randomUUID();

	private static final UUID BOB = UUID.randomUUID();

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrateWithExistingUsers() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "14").migrate();

		jdbc.update("INSERT INTO users (id, email, display_name, default_currency, created_at)"
				+ " VALUES (?, 'alice@example.com', 'A', 'EUR', now())", ALICE);
		jdbc.update("INSERT INTO users (id, email, display_name, created_at) VALUES (?, 'bob@example.com', 'B', now())",
				BOB);
		account(ALICE, "USD", "2026-02-01", false, null);
		account(ALICE, "UAH", "2026-01-01", false, null);
		account(ALICE, "EUR", "2026-03-01", false, null);
		account(ALICE, "USD", "2026-04-01", false, null);
		account(ALICE, "PLN", "2025-01-01", true, null);
		account(ALICE, "GBP", "2025-01-01", false, BOB);

		flyway(dataSource, "latest").migrate();
	}

	@Test
	void existingUsersKeepUkrainian() {
		assertThat(jdbc.queryForList("SELECT DISTINCT language FROM users", String.class)).containsOnly("uk");
	}

	@Test
	void usedCurrenciesStartWithTheDefaultThenOwnOpenAccountsOldestFirst() {
		assertThat(usedCurrencies(ALICE)).containsExactly("EUR", "UAH", "USD");
		assertThat(usedCurrencies(BOB)).containsExactly("UAH");
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
	}

	private static void account(UUID owner, String currency, String createdOn, boolean closed,
			UUID transientRecipient) {
		jdbc.update("INSERT INTO accounts (id, owner_user_id, name, balance, currency, icon, color, created_at,"
				+ " closed_at, transient_recipient_id) VALUES (?, ?, 'A', 0, ?, 'i', 'c', ?::timestamptz, ?::timestamptz, ?)",
				UUID.randomUUID(), owner, currency, createdOn, closed ? createdOn : null, transientRecipient);
	}

	private static List<String> usedCurrencies(UUID user) {
		String[] codes = jdbc.queryForObject("SELECT used_currencies FROM users WHERE id = ?",
				(rs, row) -> (String[]) rs.getArray(1).getArray(), user);
		return Arrays.asList(codes);
	}

}
