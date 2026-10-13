package com.familybudget.family;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V18 backfill (ARCHITECTURE §3.2): runs the migrations up to V17 on its own database, inserts
 * users with and without a family, then applies V18.
 */
@Testcontainers
class FamilyForEveryoneMigrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

	private static final UUID ALICE = UUID.randomUUID();

	private static final UUID OLENA = UUID.randomUUID();

	private static final UUID MEMBER = UUID.randomUUID();

	private static final UUID SMITHS = UUID.randomUUID();

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrateWithUsersWithoutFamilies() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "17").migrate();

		user(ALICE, "alice@example.com", "en");
		user(OLENA, "olena@example.com", "uk");
		user(MEMBER, "member@example.com", "en");
		jdbc.update("INSERT INTO families (id, name, created_by, created_at) VALUES (?, 'Smiths', ?, now())", SMITHS,
				MEMBER);
		jdbc.update("INSERT INTO family_members (user_id, family_id, role, joined_at) VALUES (?, ?, 'MEMBER', now())",
				MEMBER, SMITHS);

		flyway(dataSource, "latest").migrate();
	}

	@Test
	void usersWithoutAFamilyGetTheirOwnNamedInTheirLanguage() {
		assertThat(ownFamily(ALICE)).containsEntry("name", "Family").containsEntry("role", "ADMIN");
		assertThat(ownFamily(OLENA)).containsEntry("name", "Родина").containsEntry("role", "ADMIN");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM family_members m JOIN family_members o"
				+ " ON o.family_id = m.family_id WHERE m.user_id IN (?, ?)", Integer.class, ALICE, OLENA))
			.isEqualTo(2);
	}

	@Test
	void existingMembershipsStayAsTheyWere() {
		assertThat(ownFamily(MEMBER)).containsEntry("name", "Smiths").containsEntry("role", "MEMBER");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM families", Integer.class)).isEqualTo(3);
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
	}

	private static void user(UUID id, String email, String language) {
		jdbc.update("INSERT INTO users (id, email, display_name, language, used_currencies, created_at)"
				+ " VALUES (?, ?, 'U', ?, '{UAH}', now())", id, email, language);
	}

	private static Map<String, Object> ownFamily(UUID userId) {
		List<Map<String, Object>> rows = jdbc.queryForList("SELECT f.name, m.role FROM family_members m"
				+ " JOIN families f ON f.id = m.family_id WHERE m.user_id = ?", userId);
		assertThat(rows).hasSize(1);
		return rows.getFirst();
	}

}
