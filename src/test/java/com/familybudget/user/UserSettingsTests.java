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

/**
 * Language and default currency from the sign-up region, then editable in settings along
 * with the used currencies (ARCHITECTURE §3.2).
 */
@IntegrationTest
class UserSettingsTests {

	private static final String SETTINGS = "/api/v1/users/me/settings";

	private final MockMvcTester mvc;

	private final TestApi api;

	@Autowired
	UserSettingsTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void signUpPicksTheSettingsFromTheClientsRegion() {
		Session polish = registerWith("pl-PL,pl;q=0.9,en;q=0.8");
		Session ukrainian = registerWith("uk-UA,uk;q=0.9");
		Session noHeader = TestUsers.register(this.mvc);

		assertThat(this.api.get(polish, SETTINGS)).bodyJson().isLenientlyEqualTo("""
				{"language": "en", "defaultCurrency": "PLN", "usedCurrencies": ["PLN"], "theme": "system", "primaryHue": 150}
				""");
		assertThat(this.api.get(ukrainian, SETTINGS)).bodyJson().isLenientlyEqualTo("""
				{"language": "uk", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "system", "primaryHue": 150}
				""");
		assertThat(this.api.get(noHeader, SETTINGS)).bodyJson().isLenientlyEqualTo("""
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "system", "primaryHue": 150}
				""");
	}

	@Test
	void theSettingsCanBeChanged() {
		Session alice = TestUsers.register(this.mvc);

		assertThat(this.api.put(alice, SETTINGS, """
				{"language": "uk", "defaultCurrency": "EUR", "usedCurrencies": ["EUR", "UAH", "EUR", "USD"], "theme": "dark", "primaryHue": 305}
				""")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"language": "uk", "defaultCurrency": "EUR", "usedCurrencies": ["EUR", "UAH", "USD"], "theme": "dark", "primaryHue": 305}
				""");
		assertThat(this.api.get(alice, SETTINGS)).bodyJson().isStrictlyEqualTo("""
				{"language": "uk", "defaultCurrency": "EUR", "usedCurrencies": ["EUR", "UAH", "USD"], "theme": "dark", "primaryHue": 305}
				""");
	}

	@Test
	void invalidSettingsAreRejected() {
		Session alice = TestUsers.register(this.mvc);

		assertProblem(alice, """
				{"language": "en", "defaultCurrency": "XYZ", "usedCurrencies": ["XYZ"], "theme": "light", "primaryHue": 150}
				""", "invalid-currency");
		assertProblem(alice, """
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH", "XYZ"], "theme": "light", "primaryHue": 150}
				""", "invalid-currency");
		assertProblem(alice, """
				{"language": "de", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "light", "primaryHue": 150}
				""", "unsupported-language");
		assertProblem(alice, """
				{"language": "en", "defaultCurrency": "EUR", "usedCurrencies": ["UAH"], "theme": "light", "primaryHue": 150}
				""", "default-currency-not-used");
		assertProblem(alice, """
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "blue", "primaryHue": 150}
				""", "unsupported-theme");
		for (String hue : new String[] { "-1", "360", "null" }) {
			assertThat(this.api.put(alice, SETTINGS, """
					{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "light", "primaryHue": %s}
					""".formatted(hue))).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"type": "%svalidation-failed", "status": 400}
						""".formatted(TestApi.PROBLEMS));
		}
		for (String malformed : new String[] { """
				{"language": "en", "defaultCurrency": "eur", "usedCurrencies": ["eur"], "theme": "light", "primaryHue": 150}
				""", """
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": [], "theme": "light", "primaryHue": 150}
				""", """
				{"language": "EN", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "light", "primaryHue": 150}
				""", """
				{"defaultCurrency": "UAH"}
				""", """
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"]}
				""" }) {
			assertThat(this.api.put(alice, SETTINGS, malformed)).hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(this.api.get(alice, SETTINGS)).bodyJson().isLenientlyEqualTo("""
				{"language": "en", "defaultCurrency": "UAH", "usedCurrencies": ["UAH"], "theme": "system", "primaryHue": 150}
				""");
	}

	private Session registerWith(String acceptLanguage) {
		return Session.from(this.mvc.post()
			.uri("/api/v1/auth/register")
			.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "displayName": "Ola", "password": "%s"}
					""".formatted(TestUsers.uniqueEmail(), TestUsers.PASSWORD))
			.exchange());
	}

	private void assertProblem(Session session, String body, String type) {
		MvcTestResult result = this.api.put(session, SETTINGS, body);
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + type);
	}

}
