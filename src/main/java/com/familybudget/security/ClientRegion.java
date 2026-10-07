package com.familybudget.security;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import com.familybudget.user.RegionCurrency;

/**
 * The region a new user signs up from, as their browser or app reports it in
 * {@code Accept-Language}. Both sign-up paths pass through a request carrying it: the
 * SPA's register call, and the browser's redirect back from Google.
 */
final class ClientRegion {

	private ClientRegion() {
	}

	static String defaultCurrency(HttpServletRequest request) {
		// Without the header, getLocales() returns the server's default locale instead.
		List<Locale> locales = (request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) != null)
				? Collections.list(request.getLocales()) : List.of();
		return RegionCurrency.forLocales(locales);
	}

}
