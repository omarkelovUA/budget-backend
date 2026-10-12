package com.familybudget.security.web;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import com.familybudget.user.model.SignUpRegion;

/**
 * The region a new user signs up from, as their browser or app reports it in
 * {@code Accept-Language}. Both sign-up paths pass through a request carrying it: the
 * SPA's register call, and the browser's redirect back from Google.
 */
public final class ClientRegion {

	private ClientRegion() {
	}

	public static SignUpRegion of(HttpServletRequest request) {
		// Without the header, getLocales() returns the server's default locale instead.
		List<Locale> locales = (request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) != null)
				? Collections.list(request.getLocales()) : List.of();
		return SignUpRegion.forLocales(locales);
	}

}
