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

	@Test
	void googleLoginWithAnUnverifiedEmailIsRejected() throws Exception {
		Session existing = TestUsers.register(this.mvc);

		MockHttpServletResponse response = googleLogin("google-" + UUID.randomUUID(), existing.email(), false);

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:4200/login?error=oauth&reason=email-not-verified");
		assertThat(response.getCookie("refresh_token")).isNull();
	}

	private MockHttpServletResponse googleLogin(String subject, String email, boolean emailVerified) throws Exception {
		OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60),
				Map.of("sub", subject, "email", email, "email_verified", emailVerified, "name", "Google User"));
		DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
		MockHttpServletResponse response = new MockHttpServletResponse();
		this.successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
				new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
		return response;
	}

}
