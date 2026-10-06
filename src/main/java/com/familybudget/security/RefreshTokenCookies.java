package com.familybudget.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** The httpOnly refresh-token cookie, scoped to the auth endpoints only (ARCHITECTURE.md §6.2). */
@Component
class RefreshTokenCookies {

	static final String NAME = "refresh_token";

	static final String PATH = "/api/v1/auth";

	private final boolean secure;

	RefreshTokenCookies(@Value("${app.security.refresh-token.cookie-secure}") boolean secure) {
		this.secure = secure;
	}

	ResponseCookie create(String token, Duration maxAge) {
		return build(token, maxAge);
	}

	ResponseCookie clear() {
		return build("", Duration.ZERO);
	}

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(this.secure)
			.sameSite("Strict")
			.path(PATH)
			.maxAge(maxAge)
			.build();
	}

}
