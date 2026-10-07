package com.familybudget.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestApi;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

/** Default currency from the sign-up region, then editable in settings (PLAN.md M13). */
@IntegrationTest
class UserSettingsTests {

	private final MockMvcTester mvc;

	private final TestApi api;

	@Autowired
	UserSettingsTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void signUpPicksTheDefaultCurrencyFromTheClientsRegion() {
		Session polish = Session.from(this.mvc.post()
			.uri("/api/v1/auth/register")
			.header(HttpHeaders.ACCEPT_LANGUAGE, "pl-PL,pl;q=0.9,en;q=0.8")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "displayName": "Ola", "password": "%s"}
					""".formatted(TestUsers.uniqueEmail(), TestUsers.PASSWORD))
			.exchange());
		Session noHeader = TestUsers.register(this.mvc);

		assertThat(this.api.get(polish, "/api/v1/users/me/settings")).bodyJson()
			.extractingPath("$.defaultCurrency")
			.isEqualTo("PLN");
		assertThat(this.api.get(noHeader, "/api/v1/users/me/settings")).bodyJson()
			.extractingPath("$.defaultCurrency")
			.isEqualTo("UAH");
	}

	@Test
	void theDefaultCurrencyCanBeChanged() {
		Session alice = TestUsers.register(this.mvc);

		assertThat(this.api.put(alice, "/api/v1/users/me/settings", """
				{"defaultCurrency": "EUR"}
				""")).hasStatusOk().bodyJson().extractingPath("$.defaultCurrency").isEqualTo("EUR");
		assertThat(this.api.get(alice, "/api/v1/users/me/settings")).bodyJson()
			.extractingPath("$.defaultCurrency")
			.isEqualTo("EUR");

		MvcTestResult unknown = this.api.put(alice, "/api/v1/users/me/settings", """
				{"defaultCurrency": "XYZ"}
				""");
		assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "invalid-currency");
		assertThat(this.api.put(alice, "/api/v1/users/me/settings", """
				{"defaultCurrency": "eur"}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
