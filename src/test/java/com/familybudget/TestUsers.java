package com.familybudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Signs up throwaway Google users through the real API (with {@link TestGoogleIdTokens})
 * so tests don't depend on each other.
 */
public final class TestUsers {

	private TestUsers() {
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	public static Session register(MockMvcTester mvc) {
		return register(mvc, uniqueEmail());
	}

	public static Session register(MockMvcTester mvc, String email) {
		return signUp(mvc, email, null);
	}

	/** Signs up from a browser whose language is {@code acceptLanguage}. */
	public static Session registerIn(MockMvcTester mvc, String acceptLanguage) {
		return signUp(mvc, uniqueEmail(), acceptLanguage);
	}

	private static Session signUp(MockMvcTester mvc, String email, @Nullable String acceptLanguage) {
		String idToken = TestGoogleIdTokens.sign((claims) -> claims.claim("email", email).claim("name", "Test User"));
		var request = mvc.post()
			.uri("/api/v1/auth/google/mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"idToken": "%s", "confirmLink": false}
					""".formatted(idToken));
		if (acceptLanguage != null) {
			request = request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		MvcTestResult result = request.exchange();
		assertThat(result).hasStatusOk();
		// Web and mobile sessions share one refresh-token store; only the transport differs.
		return new Session(UUID.fromString(json(result, "$.user.id")), json(result, "$.user.email"),
				json(result, "$.accessToken"), new Cookie("refresh_token", json(result, "$.refreshToken")));
	}

	public static String json(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public record Session(UUID userId, String email, String accessToken, Cookie refreshCookie) {

		public String bearer() {
			return "Bearer " + this.accessToken;
		}

	}

}
