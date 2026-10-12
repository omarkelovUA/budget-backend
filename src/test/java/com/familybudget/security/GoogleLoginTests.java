package com.familybudget.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;
import com.familybudget.security.web.OAuth2LoginSuccessHandler;

/**
 * Google login path (ARCHITECTURE.md §6.1). Google itself can't be reached from tests, so
 * the success handler is driven with the OIDC user Spring Security would hand it.
 */
@IntegrationTest
class GoogleLoginTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private OAuth2LoginSuccessHandler successHandler;

	@Test
	void loginStartsAnAuthorizationCodeFlowWithPkce() {
		MvcTestResult result = this.mvc.get().uri("/oauth2/authorization/google").exchange();

		assertThat(result).hasStatus3xxRedirection();
		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).startsWith("https://accounts.google.com/")
			.contains("response_type=code")
			.contains("code_challenge=")
			.contains("code_challenge_method=S256");
	}

	@Test
	void firstGoogleLoginCreatesTheUserAndConvergesOnTheRefreshFlow() throws Exception {
		String email = TestUsers.uniqueEmail();

		MockHttpServletResponse response = googleLogin("google-" + UUID.randomUUID(), email, true);

		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/auth/callback");
		Cookie cookie = response.getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		assertThat(this.mvc.post().uri("/api/v1/auth/refresh").cookie(cookie)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.user.email")
			.isEqualTo(email);
	}

	@Test
	void googleLoginWithAVerifiedEmailLinksTheExistingPasswordAccount() throws Exception {
		Session existing = TestUsers.register(this.mvc);
		String subject = "google-" + UUID.randomUUID();

		Cookie first = googleLogin(subject, existing.email(), true).getCookie("refresh_token");
		// Once linked, the Google subject identifies the user even if the email changes.
		Cookie second = googleLogin(subject, "changed-" + existing.email(), true).getCookie("refresh_token");

		for (Cookie cookie : List.of(first, second)) {
			assertThat(this.mvc.post().uri("/api/v1/auth/refresh").cookie(cookie)).hasStatusOk()
				.bodyJson()
				.extractingPath("$.user.id")
				.isEqualTo(existing.userId().toString());
		}
	}

	/**
	 * Someone registers the victim's email with a password (never verified); the victim
	 * later signs in with Google. The squatter must lose the account completely: password,
	 * refresh sessions and access tokens already handed out.
	 */
	@Test
	void linkingGoogleLocksOutWhoeverRegisteredTheEmailWithAPassword() throws Exception {
		Session squatter = TestUsers.register(this.mvc);

		Cookie owners = googleLogin("google-" + UUID.randomUUID(), squatter.email(), true).getCookie("refresh_token");

		assertThat(this.mvc.post()
			.uri("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(squatter.email(), TestUsers.PASSWORD))).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, squatter.bearer()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.post().uri("/api/v1/auth/refresh").cookie(squatter.refreshCookie()))
			.hasStatus(HttpStatus.UNAUTHORIZED);

		// The owner's fresh session works, and so does the token it refreshes into.
		MvcTestResult refreshed = this.mvc.post().uri("/api/v1/auth/refresh").cookie(owners).exchange();
		assertThat(refreshed).hasStatusOk();
		assertThat(this.mvc.get()
			.uri("/api/v1/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestUsers.json(refreshed, "$.accessToken")))
			.hasStatusOk();
	}

	@Test
	void googleSignUpPicksTheSettingsFromTheBrowsersRegion() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9");
		Cookie cookie = googleLogin(request, "google-" + UUID.randomUUID(), TestUsers.uniqueEmail(), true)
			.getCookie("refresh_token");
		String accessToken = TestUsers.json(this.mvc.post().uri("/api/v1/auth/refresh").cookie(cookie).exchange(),
				"$.accessToken");

		assertThat(this.mvc.get()
			.uri("/api/v1/users/me/settings")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).bodyJson().isLenientlyEqualTo("""
					{"language": "en", "defaultCurrency": "EUR", "usedCurrencies": ["EUR"], "theme": "system"}
					""");
		// Google sign-up gets its own family too, named in the user's language.
		assertThat(this.mvc.get()
			.uri("/api/v1/families/current")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Family");
		// And the starting accounts, in the region's currency.
		assertThat(this.mvc.get()
			.uri("/api/v1/accounts")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).bodyJson()
			.extractingPath("$[*].currency")
			.asArray()
			.containsExactly("EUR", "EUR");
	}

	@Test
	void googleLoginWithAnUnverifiedEmailIsRejected() throws Exception {
		Session existing = TestUsers.register(this.mvc);

		MockHttpServletResponse response = googleLogin("google-" + UUID.randomUUID(), existing.email(), false);

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:4200/login?error=oauth&reason=email-not-verified");
		assertThat(response.getCookie("refresh_token")).isNull();
	}

	private MockHttpServletResponse googleLogin(String subject, String email, boolean emailVerified) throws Exception {
		return googleLogin(new MockHttpServletRequest(), subject, email, emailVerified);
	}

	private MockHttpServletResponse googleLogin(MockHttpServletRequest request, String subject, String email,
			boolean emailVerified) throws Exception {
		OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60),
				Map.of("sub", subject, "email", email, "email_verified", emailVerified, "name", "Google User"));
		DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
		MockHttpServletResponse response = new MockHttpServletResponse();
		this.successHandler.onAuthenticationSuccess(request, response,
				new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
		return response;
	}

}
