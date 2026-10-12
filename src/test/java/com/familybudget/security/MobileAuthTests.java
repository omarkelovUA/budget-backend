package com.familybudget.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

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
import com.familybudget.TestGoogleIdTokens;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

/**
 * Mobile sign-in (ARCHITECTURE.md §6.1): a Google ID token in, both tokens in the body,
 * never a cookie.
 */
@IntegrationTest
class MobileAuthTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void newGoogleUserGetsBothTokensInTheBody() {
		String email = TestUsers.uniqueEmail();

		MvcTestResult result = signIn(TestGoogleIdTokens.sign((claims) -> claims.claim("email", email)), false,
				"de-DE");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
		assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
		assertThat(result).bodyJson().extractingPath("$.user.email").isEqualTo(email);
		assertThat(TestUsers.json(result, "$.refreshToken")).isNotEmpty();
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(this.mvc.get()
			.uri("/api/v1/users/me/settings")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestUsers.json(result, "$.accessToken"))).bodyJson()
			.extractingPath("$.defaultCurrency")
			.isEqualTo("EUR");
	}

	@Test
	void returningGoogleUserIsTheSameUser() {
		String subject = "google-returning-" + TestUsers.uniqueEmail();

		String first = TestUsers.json(signIn(TestGoogleIdTokens.sign((claims) -> claims.subject(subject)), false),
				"$.user.id");
		String second = TestUsers.json(signIn(TestGoogleIdTokens.sign((claims) -> claims.subject(subject)), false),
				"$.user.id");

		assertThat(second).isEqualTo(first);
	}

	@Test
	void tokenForAnotherClientIsRejected() {
		MvcTestResult result = signIn(TestGoogleIdTokens.sign((claims) -> claims.audience(List.of("someone-else"))),
				false);

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(TestApi.PROBLEMS + "invalid-google-token");
	}

	@Test
	void unverifiedEmailIsRejected() {
		MvcTestResult result = signIn(TestGoogleIdTokens.sign((claims) -> claims.claim("email_verified", false)),
				false);

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(TestApi.PROBLEMS + "email-not-verified");
	}

	@Test
	void linkingAPasswordAccountNeedsConsent() {
		Session existing = TestUsers.register(this.mvc);
		String token = TestGoogleIdTokens.sign((claims) -> claims.claim("email", existing.email()));

		MvcTestResult refused = signIn(token, false);

		assertThat(refused).hasStatus(HttpStatus.CONFLICT);
		assertThat(refused).bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "google-link-confirmation-required");
		assertThat(passwordLogin(existing.email())).hasStatusOk();

		MvcTestResult linked = signIn(token, true);

		assertThat(linked).hasStatusOk();
		assertThat(linked).bodyJson().extractingPath("$.user.id").isEqualTo(existing.userId().toString());
		assertThat(passwordLogin(existing.email())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void blankIdTokenIsAValidationError() {
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/auth/google/mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(TestApi.PROBLEMS + "validation-failed");
	}

	@Test
	void mobileRefreshRotatesTheToken() {
		String old = TestUsers.json(signIn(TestGoogleIdTokens.sign((claims) -> {
		}), false), "$.refreshToken");

		MvcTestResult refreshed = mobile("refresh", old);

		assertThat(refreshed).hasStatusOk();
		assertThat(TestUsers.json(refreshed, "$.refreshToken")).isNotEqualTo(old);
		assertThat(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(this.mvc.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestUsers.json(refreshed, "$.accessToken")))
			.hasStatusOk();
		MvcTestResult replayed = mobile("refresh", old);
		assertThat(replayed).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(replayed).bodyJson().extractingPath("$.type").isEqualTo(TestApi.PROBLEMS + "invalid-refresh-token");
	}

	@Test
	void replayingAnOldMobileTokenLaterRevokesAllSessions() {
		MvcTestResult session = signIn(TestGoogleIdTokens.sign((claims) -> {
		}), false);
		String old = TestUsers.json(session, "$.refreshToken");
		String rotated = TestUsers.json(mobile("refresh", old), "$.refreshToken");
		this.jdbc.update("update refresh_tokens set revoked_at = now() - interval '1 hour' where user_id = ? and revoked_at is not null",
				UUID.fromString(TestUsers.json(session, "$.user.id")));

		assertThat(mobile("refresh", old)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mobile("refresh", rotated)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void mobileLogoutRevokesTheToken() {
		String token = TestUsers.json(signIn(TestGoogleIdTokens.sign((claims) -> {
		}), false), "$.refreshToken");

		MvcTestResult logout = mobile("logout", token);

		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(mobile("refresh", token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void mobileRefreshWithoutATokenIsAValidationError() {
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/auth/mobile/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(TestApi.PROBLEMS + "validation-failed");
	}

	private MvcTestResult signIn(String idToken, boolean confirmLink, String... acceptLanguage) {
		var request = this.mvc.post()
			.uri("/api/v1/auth/google/mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"idToken": "%s", "confirmLink": %s}
					""".formatted(idToken, confirmLink));
		if (acceptLanguage.length > 0) {
			request = request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		return request.exchange();
	}

	private MvcTestResult mobile(String action, String refreshToken) {
		return this.mvc.post()
			.uri("/api/v1/auth/mobile/{action}", action)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"refreshToken": "%s"}
					""".formatted(refreshToken))
			.exchange();
	}

	private MvcTestResult passwordLogin(String email) {
		return this.mvc.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(email, TestUsers.PASSWORD))
			.exchange();
	}

}
