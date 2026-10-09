package com.familybudget.common;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Requests Spring rejects before they reach a service (invalid or unreadable bodies, wrong
 * parameter types) answer with the stable {@code validation-failed} problem type.
 */
@IntegrationTest
class ValidationProblemTests {

	private final MockMvcTester mvc;

	private final TestApi api;

	@Autowired
	ValidationProblemTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void invalidBodiesAreValidationFailures() {
		Session alice = TestUsers.register(this.mvc);

		assertValidationFailed(this.api.put(alice, "/api/v1/users/me/settings", """
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "light", "primaryHue": 360}
				"""));
		assertValidationFailed(this.api.post(alice, "/api/v1/accounts", """
				{"name": "", "balance": 0, "currency": "UAH", "icon": "assets/card.svg", "color": "green"}
				"""));
	}

	@Test
	void unreadableBodiesAreValidationFailures() {
		Session alice = TestUsers.register(this.mvc);

		assertValidationFailed(this.api.put(alice, "/api/v1/users/me/settings", "{\"language\": "));
		assertValidationFailed(this.api.put(alice, "/api/v1/users/me/settings", """
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "light", "primaryHue": "green"}
				"""));
	}

	@Test
	void wrongParameterTypesAreValidationFailures() {
		Session alice = TestUsers.register(this.mvc);

		assertValidationFailed(this.api.get(alice, "/api/v1/accounts/{id}", "not-a-uuid"));
	}

	private static void assertValidationFailed(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "validation-failed");
	}

}
