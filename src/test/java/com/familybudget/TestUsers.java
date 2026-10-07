package com.familybudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Registers throwaway users through the real API so tests don't depend on each other. */
public final class TestUsers {

	public static final String PASSWORD = "correct-horse-battery";

	private TestUsers() {
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	public static Session register(MockMvcTester mvc) {
		return register(mvc, uniqueEmail());
	}

	public static Session register(MockMvcTester mvc, String email) {
		MvcTestResult result = mvc.post()
			.uri("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "displayName": "Test User", "password": "%s"}
					""".formatted(email, PASSWORD))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return Session.from(result);
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

		public static Session from(MvcTestResult result) {
			Cookie cookie = result.getResponse().getCookie("refresh_token");
			assertThat(cookie).isNotNull();
			return new Session(UUID.fromString(json(result, "$.user.id")), json(result, "$.user.email"),
					json(result, "$.accessToken"), cookie);
		}

		public String bearer() {
			return "Bearer " + this.accessToken;
		}

	}

}
