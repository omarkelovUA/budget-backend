package com.familybudget.family;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V12: categories and budgets left behind by families deleted before {@link FamilyDeleted}
 * existed. Runs the migrations up to V11 on its own database, inserts orphans, applies V12.
 */
@Testcontainers
class OrphanedFamilyDataMigrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	private static final UUID ALICE = UUID.randomUUID();

	private static final UUID EVE = UUID.randomUUID();

	private static final UUID LIVING_FAMILY = UUID.randomUUID();

	private static final UUID GONE_FAMILY = UUID.randomUUID();

	private static final UUID USED_ORPHAN = UUID.randomUUID();

	private static final UUID UNUSED_ORPHAN = UUID.randomUUID();

	private static final UUID LIVING_CATEGORY = UUID.randomUUID();

	private static final UUID USED_ORPHAN_BUDGET = UUID.randomUUID();

	private static final UUID UNUSED_ORPHAN_BUDGET = UUID.randomUUID();

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrateWithOrphans() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "11").migrate();

		for (UUID user : List.of(ALICE, EVE)) {
			jdbc.update("INSERT INTO users (id, email, display_name, created_at) VALUES (?, ?, 'U', now())", user,
					user + "@example.com");
		}
		jdbc.update("INSERT INTO families (id, name, created_by, created_at) VALUES (?, 'F', ?, now())",
				LIVING_FAMILY, ALICE);
		category(USED_ORPHAN, GONE_FAMILY);
		category(UNUSED_ORPHAN, GONE_FAMILY);
		category(LIVING_CATEGORY, LIVING_FAMILY);
		budget(USED_ORPHAN_BUDGET, USED_ORPHAN);
		budget(UNUSED_ORPHAN_BUDGET, UNUSED_ORPHAN);

		UUID account = UUID.randomUUID();
		jdbc.update("INSERT INTO accounts (id, owner_user_id, name, balance, currency, icon, color, created_at)"
				+ " VALUES (?, ?, 'A', 0, 'UAH', 'i', 'c', now())", account, EVE);
		// Alice paid first, Eve last: the latest payer gets the category.
		payment(ALICE, account, "2026-01-01T00:00:00Z");
		payment(EVE, account, "2026-02-01T00:00:00Z");

		flyway(dataSource, "latest").migrate();
	}

	@Test
	void usedOrphansGoToTheirLatestPayerWithTheirBudgets() {
		assertThat(owner("categories", USED_ORPHAN)).containsExactly("USER", EVE.toString());
		assertThat(owner("budgets", USED_ORPHAN_BUDGET)).containsExactly("USER", EVE.toString());
	}

	@Test
	void unusedOrphansAreDropped() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM categories WHERE id = ?", Integer.class, UNUSED_ORPHAN))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM budgets WHERE id = ?", Integer.class,
				UNUSED_ORPHAN_BUDGET))
			.isZero();
	}

	@Test
	void livingFamiliesKeepTheirData() {
		assertThat(owner("categories", LIVING_CATEGORY)).containsExactly("FAMILY", LIVING_FAMILY.toString());
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
	}

	private static void category(UUID id, UUID familyId) {
		jdbc.update("INSERT INTO categories (id, owner_type, owner_id, type, name, icon, color, created_at)"
				+ " VALUES (?, 'FAMILY', ?, 'EXPENSE', 'Food', 'i', 'c', now())", id, familyId);
	}

	private static void budget(UUID id, UUID categoryId) {
		jdbc.update("INSERT INTO budgets (id, owner_type, owner_id, category_id, period, planned_amount, created_at)"
				+ " VALUES (?, 'FAMILY', ?, ?, '2026-01', 10, now())", id, GONE_FAMILY, categoryId);
	}

	private static void payment(UUID recordedBy, UUID account, String createdAt) {
		jdbc.update("INSERT INTO transactions (id, kind, amount, occurred_on, recorded_by, account_id, category_id,"
				+ " created_at) VALUES (?, 'PAYMENT', 5, '2026-01-01', ?, ?, ?, ?::timestamptz)", UUID.randomUUID(),
				recordedBy, account, USED_ORPHAN, createdAt);
	}

	private static List<String> owner(String table, UUID id) {
		return jdbc.queryForObject("SELECT owner_type, owner_id FROM " + table + " WHERE id = ?",
				(row, n) -> List.of(row.getString("owner_type"), row.getString("owner_id")), id);
	}

}
