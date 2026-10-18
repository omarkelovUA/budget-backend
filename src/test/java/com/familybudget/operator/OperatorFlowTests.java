package com.familybudget.operator;

import static com.familybudget.TestApi.PROBLEMS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestApi;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

/** Usage numbers and manual grants for operators only (ARCHITECTURE.md §3.5). */
@IntegrationTest
class OperatorFlowTests {

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private final TestApi api;

	@Autowired
	OperatorFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = new TestApi(mvc, jdbc);
	}

	/** Signs in as the configured operator; every call is the same user. */
	private Session operator() {
		return TestUsers.register(this.mvc, IntegrationTest.OPERATOR);
	}

	@Test
	void onlyOperatorsGetIn() {
		Session someone = TestUsers.register(this.mvc);

		assertThat(this.api.get(someone, "/api/v1/users/me")).bodyJson().extractingPath("$.operator").isEqualTo(false);
		assertThat(this.api.get(operator(), "/api/v1/users/me")).bodyJson().extractingPath("$.operator").isEqualTo(true);
		for (MvcTestResult result : new MvcTestResult[] { this.api.get(someone, "/api/v1/operator/stats"),
				this.api.get(someone, "/api/v1/operator/grants"),
				this.api.post(someone, "/api/v1/operator/grants", grantJson(someone.email(), null)),
				this.api.delete(someone, "/api/v1/operator/grants/{id}", someone.userId()) }) {
			assertProblem(result, HttpStatus.FORBIDDEN, "operator-required");
		}
	}

	@Test
	void theOperatorsEmailCountsOnlyOnceGoogleVerifiedIt() {
		Session operator = this.api.unlinkGoogle(operator());

		assertThat(this.api.get(operator, "/api/v1/users/me")).bodyJson().extractingPath("$.operator").isEqualTo(false);
		assertProblem(this.api.get(operator, "/api/v1/operator/stats"), HttpStatus.FORBIDDEN, "operator-required");
	}

	@Test
	void statsCountUsersFamiliesAndSubscriptions() {
		Session operator = operator();
		MvcTestResult before = stats(operator);

		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		this.jdbc.update("DELETE FROM subscriptions WHERE user_id = ?", alice.userId());
		this.api.subscribe(TestUsers.register(this.mvc));
		MvcTestResult after = stats(operator);

		assertThat(number(after, "$.users.total") - number(before, "$.users.total")).isEqualTo(3);
		assertThat(number(after, "$.users.newLast7Days") - number(before, "$.users.newLast7Days")).isEqualTo(3);
		assertThat(number(after, "$.families.withMembers") - number(before, "$.families.withMembers")).isEqualTo(1);
		assertThat(number(after, "$.families.frozen") - number(before, "$.families.frozen")).isEqualTo(1);
		assertThat(number(after, "$.subscriptions.activeManual") - number(before, "$.subscriptions.activeManual"))
			.isEqualTo(1);
		String today = LocalDate.now(ZoneOffset.UTC).toString();
		assertThat(after).bodyJson().extractingPath("$.users.signups.length()").isEqualTo(30);
		assertThat(after).bodyJson().extractingPath("$.users.activeUsers[29].day").isEqualTo(today);
		assertThat(number(after, "$.users.signups[29].count") - number(before, "$.users.signups[29].count"))
			.isEqualTo(3);
		assertThat(number(after, "$.users.activeUsers[29].count") - number(before, "$.users.activeUsers[29].count"))
			.isEqualTo(3);
	}

	@Test
	void aSessionCountsItsUserActiveOncePerDay() {
		Session alice = TestUsers.register(this.mvc);

		assertThat(this.mvc.post().uri("/api/v1/auth/refresh").cookie(alice.refreshCookie())).hasStatusOk();

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM user_activity WHERE user_id = ?", Integer.class,
				alice.userId()))
			.isEqualTo(1);
	}

	@Test
	void manualGrantsAreGivenChangedAndTakenBack() {
		Session operator = operator();
		Session alice = TestUsers.register(this.mvc);

		MvcTestResult granted = this.api.post(operator, "/api/v1/operator/grants", grantJson(alice.email(), null));
		assertThat(granted).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"userId": "%s", "email": "%s", "displayName": "Test User", "expiresAt": null, "active": true}
				""".formatted(alice.userId(), alice.email()));
		assertThat(this.api.get(alice, "/api/v1/subscriptions/me")).bodyJson()
			.extractingPath("$.source")
			.isEqualTo("manual");
		assertThat(this.api.invite(alice, TestUsers.uniqueEmail())).hasStatus(HttpStatus.CREATED);

		assertThat(this.api.post(operator, "/api/v1/operator/grants",
				grantJson(alice.email().toUpperCase(), "\"2020-01-01T00:00:00Z\"")))
			.bodyJson()
			.extractingPath("$.active")
			.isEqualTo(false);
		assertThat(this.api.get(operator, "/api/v1/operator/grants")).bodyJson()
			.extractingPath("$[?(@.userId == '%s')].expiresAt".formatted(alice.userId()))
			.asArray()
			.containsExactly("2020-01-01T00:00:00Z");

		assertThat(this.api.delete(operator, "/api/v1/operator/grants/{id}", alice.userId()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(alice, "/api/v1/subscriptions/me")).hasStatus(HttpStatus.NO_CONTENT);
		assertProblem(this.api.delete(operator, "/api/v1/operator/grants/{id}", alice.userId()), HttpStatus.NOT_FOUND,
				"not-found");
		assertProblem(this.api.post(operator, "/api/v1/operator/grants", grantJson(TestUsers.uniqueEmail(), null)),
				HttpStatus.NOT_FOUND, "user-not-found");
		assertThat(this.api.post(operator, "/api/v1/operator/grants", grantJson("not-an-email", null)))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	private MvcTestResult stats(Session operator) {
		MvcTestResult result = this.api.get(operator, "/api/v1/operator/stats");
		assertThat(result).hasStatusOk();
		return result;
	}

	private static long number(MvcTestResult result, String path) {
		return Long.parseLong(TestUsers.json(result, path));
	}

	private static String grantJson(String email, String expiresAt) {
		return """
				{"email": "%s", "expiresAt": %s}
				""".formatted(email, expiresAt);
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
