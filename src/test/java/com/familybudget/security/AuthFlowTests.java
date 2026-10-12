package com.familybudget.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;
import com.familybudget.security.service.AccessTokenService;
import com.familybudget.security.service.RefreshTokenCleanup;

/** Password login path and refresh-token rotation (ARCHITECTURE.md §6.1, §6.2). */
@IntegrationTest
class AuthFlowTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RefreshTokenCleanup cleanup;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	void registerIssuesAccessTokenAndHardenedRefreshCookie() {
		String email = TestUsers.uniqueEmail();
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "displayName": "Alice", "password": "%s"}
					""".formatted(email.toUpperCase(), TestUsers.PASSWORD))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.user.email").isEqualTo(email);
		assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
		assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("refresh_token=")
			.contains("HttpOnly")
			.contains("Secure")
			.contains("SameSite=Strict")
			.contains("Path=/api/v1/auth");
	}

	@Test
	void registerRejectsDuplicateEmailCaseInsensitively() {
		Session existing = TestUsers.register(this.mvc);

		MvcTestResult result = register(existing.email().toUpperCase(), TestUsers.PASSWORD);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson()
			.extractingPath("$.type")
			.isEqualTo("https://familybudget.app/problems/email-taken");
	}

	@Test
	void registerValidatesInput() {
		assertThat(register("not-an-email", TestUsers.PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(register(TestUsers.uniqueEmail(), "short")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void loginWithCorrectPasswordGivesAccessToTheApi() {
		Session registered = TestUsers.register(this.mvc);

		MvcTestResult login = login(registered.email(), TestUsers.PASSWORD);
		assertThat(login).hasStatusOk();
		Session session = sessionFrom(login);

		assertThat(this.mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo(registered.email());
	}

	@Test
	void loginRejectsWrongPasswordAndUnknownEmailTheSameWay() {
		Session registered = TestUsers.register(this.mvc);

		for (MvcTestResult result : new MvcTestResult[] { login(registered.email(), "wrong-password"),
				login(TestUsers.uniqueEmail(), TestUsers.PASSWORD) }) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(result).bodyJson()
				.extractingPath("$.type")
				.isEqualTo("https://familybudget.app/problems/invalid-credentials");
		}
	}

	@Test
	void invalidOrMissingAccessTokenGetsAProblemResponse() {
		for (MvcTestResult result : new MvcTestResult[] {
				this.mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").exchange(),
				this.mvc.get().uri("/api/v1/users/me").exchange() }) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
			assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
			assertThat(result).bodyJson()
				.extractingPath("$.type")
				.isEqualTo("https://familybudget.app/problems/unauthenticated");
		}
	}

	@Test
	void accessTokensWithoutTheCurrentSessionVersionAreRejected() {
		Session session = TestUsers.register(this.mvc);
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(AccessTokenService.ISSUER)
			.subject(session.userId().toString())
			.issuedAt(now)
			.expiresAt(now.plusSeconds(60));
		// e.g. a token issued before session versions existed, or before an invalidation.
		for (JwtClaimsSet stale : List.of(claims.build(), claims.claim("sv", 7).build())) {
			String token = this.jwtEncoder
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), stale))
				.getTokenValue();
			assertThat(this.mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(this.mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
			.hasStatusOk();
	}

	@Test
	void cleanupPurgesExpiredRefreshTokensButKeepsRevokedOnes() {
		Session expired = TestUsers.register(this.mvc);
		Session rotated = TestUsers.register(this.mvc);
		refresh(rotated.refreshCookie());
		this.jdbc.update("update refresh_tokens set expires_at = now() - interval '1 day' where user_id = ?",
				expired.userId());

		this.cleanup.purgeExpired();

		assertThat(countTokens(expired)).isZero();
		// The rotated (revoked) token stays, so replaying it can still be detected.
		assertThat(countTokens(rotated)).isEqualTo(2);
	}

	@Test
	void refreshRotatesTheRefreshToken() {
		Session session = TestUsers.register(this.mvc);

		MvcTestResult refreshed = refresh(session.refreshCookie());
		assertThat(refreshed).hasStatusOk();
		assertThat(refreshed).bodyJson().extractingPath("$.user.id").isEqualTo(session.userId().toString());
		Cookie rotated = refreshed.getResponse().getCookie("refresh_token");
		assertThat(rotated).isNotNull();
		assertThat(rotated.getValue()).isNotEqualTo(session.refreshCookie().getValue());

		// Replay within the grace window (e.g. a second tab): rejected, new token unaffected.
		assertThat(refresh(session.refreshCookie())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(refresh(rotated)).hasStatusOk();
	}

	@Test
	void replayingARotatedTokenLaterRevokesAllSessions() {
		Session session = TestUsers.register(this.mvc);
		Cookie rotated = refresh(session.refreshCookie()).getResponse().getCookie("refresh_token");
		this.jdbc.update("update refresh_tokens set revoked_at = now() - interval '1 hour' where user_id = ? and revoked_at is not null",
				session.userId());

		assertThat(refresh(session.refreshCookie())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(refresh(rotated)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshWorksEvenWithAnExpiredAccessTokenAttached() {
		Session session = TestUsers.register(this.mvc);

		assertThat(this.mvc.post()
			.uri("/api/v1/auth/refresh")
			.header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-garbage")
			.cookie(session.refreshCookie())).hasStatusOk();
	}

	@Test
	void refreshWithoutCookieIsUnauthorized() {
		assertThat(this.mvc.post().uri("/api/v1/auth/refresh")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void logoutRevokesTheRefreshTokenAndClearsTheCookie() {
		Session session = TestUsers.register(this.mvc);

		MvcTestResult logout = this.mvc.post().uri("/api/v1/auth/logout").cookie(session.refreshCookie()).exchange();
		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("refresh_token=;")
			.contains("Max-Age=0");

		assertThat(refresh(session.refreshCookie())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult register(String email, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "displayName": "Test", "password": "%s"}
					""".formatted(email, password))
			.exchange();
	}

	private MvcTestResult login(String email, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(email, password))
			.exchange();
	}

	private MvcTestResult refresh(Cookie cookie) {
		return this.mvc.post().uri("/api/v1/auth/refresh").cookie(cookie).exchange();
	}

	private Integer countTokens(Session session) {
		return this.jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Integer.class,
				session.userId());
	}

	private static Session sessionFrom(MvcTestResult result) {
		Cookie cookie = result.getResponse().getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		return new Session(java.util.UUID.fromString(TestUsers.json(result, "$.user.id")),
				TestUsers.json(result, "$.user.email"), TestUsers.json(result, "$.accessToken"), cookie);
	}

}
