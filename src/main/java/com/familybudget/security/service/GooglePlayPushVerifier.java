package com.familybudget.security.service;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import com.familybudget.common.exception.ApiException;
import com.familybudget.security.model.GoogleIdentity;

/**
 * Checks the OIDC token Pub/Sub attaches to a push of Google Play's notifications
 * (ARCHITECTURE.md §6.1): a Google-signed token for our push audience, issued to the
 * service account the push subscription runs as.
 */
public class GooglePlayPushVerifier {

	private final GoogleIdTokenVerifier tokens;

	private final String serviceAccount;

	/** {@code tokens} must check the push audience, not the sign-in client id. */
	public GooglePlayPushVerifier(GoogleIdTokenVerifier tokens, String serviceAccount) {
		this.tokens = tokens;
		this.serviceAccount = serviceAccount;
	}

	/** Refuses anything else with {@code 401 unauthenticated}. */
	public void verify(@Nullable String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw unauthenticated();
		}
		GoogleIdentity identity;
		try {
			identity = this.tokens.verify(authorization.substring("Bearer ".length()));
		}
		catch (ApiException ex) {
			// Google's keys being unreachable stays a 503, so Pub/Sub retries.
			if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
				throw unauthenticated();
			}
			throw ex;
		}
		if (!identity.emailVerified() || !this.serviceAccount.equals(identity.email())) {
			throw unauthenticated();
		}
	}

	private static ApiException unauthenticated() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Not a push from our Pub/Sub subscription");
	}

}
