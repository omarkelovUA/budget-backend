package com.familybudget.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * V10 backfill (PLAN.md M12, open decision #5): runs the migrations up to V9 on its own
 * database, inserts accounts in the old user/family shape, then applies V10.
 */
@Testcontainers
class AccountAccessMigrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	private static final UUID ALICE = UUID.randomUUID();

	private static final UUID BOB = UUID.randomUUID();

	private static final UUID CAROL = UUID.randomUUID();

	private static final UUID DAVE = UUID.randomUUID();

	private static final UUID EVE = UUID.randomUUID();

	private static final UUID FAMILY = UUID.randomUUID();

	private static final UUID PRIVATE_ACCOUNT = UUID.randomUUID();

	private static final UUID FAMILY_ACCOUNT = UUID.randomUUID();

	private static final UUID ORPHAN_WITH_HISTORY = UUID.randomUUID();

	private static final UUID ORPHAN_WITHOUT_HISTORY = UUID.randomUUID();

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrateWithOldShapeData() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "9").migrate();

		for (UUID user : List.of(ALICE, BOB, CAROL, DAVE, EVE)) {
			jdbc.update("INSERT INTO users (id, email, display_name, created_at) VALUES (?, ?, 'U', now())", user,
					user + "@example.com");
		}
		jdbc.update("INSERT INTO families (id, name, created_by, created_at) VALUES (?, 'F', ?, now())", FAMILY, ALICE);
		// Bob is an admin too, but Alice has been one longer.
		member(ALICE, "ADMIN", "2026-01-01");
		member(BOB, "ADMIN", "2026-02-01");
		member(CAROL, "MEMBER", "2025-12-01");

		account(PRIVATE_ACCOUNT, "USER", DAVE);
		account(FAMILY_ACCOUNT, "FAMILY", FAMILY);
		account(ORPHAN_WITH_HISTORY, "FAMILY", UUID.randomUUID());
		account(ORPHAN_WITHOUT_HISTORY, "FAMILY", UUID.randomUUID());
		UUID category = UUID.randomUUID();
		jdbc.update("INSERT INTO categories (id, owner_type, owner_id, type, name, icon, color, created_at)"
				+ " VALUES (?, 'USER', ?, 'EXPENSE', 'Food', 'i', 'c', now())", category, EVE);
		jdbc.update("INSERT INTO transactions (id, kind, amount, occurred_on, recorded_by, account_id, category_id,"
				+ " created_at) VALUES (?, 'PAYMENT', 5, '2026-01-01', ?, ?, ?, now())", UUID.randomUUID(), EVE,
				ORPHAN_WITH_HISTORY, category);

		flyway(dataSource, "latest").migrate();
	}

	@Test
	void privateAccountsKeepTheirOwnerAndHaveNoGrants() {
		assertThat(owner(PRIVATE_ACCOUNT)).isEqualTo(DAVE);
		assertThat(grants(PRIVATE_ACCOUNT)).isEmpty();
	}

	@Test
	void familyAccountsGoToTheLongestStandingAdminAndEveryoneElseKeepsSharedAccess() {
		assertThat(owner(FAMILY_ACCOUNT)).isEqualTo(ALICE);
		assertThat(grants(FAMILY_ACCOUNT)).containsOnly(Map.entry(BOB, "SHARED"), Map.entry(CAROL, "SHARED"));
	}

	@Test
	void orphanedAccountsGoToTheirFirstRecorderOrAreDropped() {
		assertThat(owner(ORPHAN_WITH_HISTORY)).isEqualTo(EVE);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts WHERE id = ?", Integer.class,
				ORPHAN_WITHOUT_HISTORY))
			.isZero();
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
	}

	private static void member(UUID user, String role, String joinedOn) {
		jdbc.update("INSERT INTO family_members (user_id, family_id, role, joined_at) VALUES (?, ?, ?, ?::timestamptz)",
				user, FAMILY, role, joinedOn);
	}

	private static void account(UUID id, String ownerType, UUID ownerId) {
		jdbc.update("INSERT INTO accounts (id, owner_type, owner_id, name, balance, currency, icon, color, created_at)"
				+ " VALUES (?, ?, ?, 'A', 0, 'UAH', 'i', 'c', now())", id, ownerType, ownerId);
	}

	private static UUID owner(UUID account) {
		return jdbc.queryForObject("SELECT owner_user_id FROM accounts WHERE id = ?", UUID.class, account);
	}

	private static Map<UUID, String> grants(UUID account) {
		Map<UUID, String> grants = new HashMap<>();
		jdbc.queryForList("SELECT user_id, level FROM account_access WHERE account_id = ?", account)
			.forEach((row) -> grants.put((UUID) row.get("user_id"), (String) row.get("level")));
		return grants;
	}

}
