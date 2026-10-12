package com.familybudget.security.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.familybudget.common.exception.ApiException;
import com.familybudget.security.model.GoogleIdentity;

/**
 * Checks the Google ID tokens the mobile app gets from Credential Manager
 * (ARCHITECTURE.md §6.1): Google's signature, issuer, our client ID as audience, and
 * expiry. Only then is the token's identity trusted.
 */
public class GoogleIdTokenVerifier {

	private static final Logger logger = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

	public static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

	// Google issues both spellings.
	private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

	private final NimbusJwtDecoder decoder;

	/** Installs the Google token validator on {@code decoder}. */
	public GoogleIdTokenVerifier(NimbusJwtDecoder decoder, String clientId) {
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setAllowEmptyExpiryClaim(false);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
				new JwtClaimValidator<String>(JwtClaimNames.SUB, (sub) -> sub != null && !sub.isBlank()),
				new JwtClaimValidator<>(JwtClaimNames.ISS, (Object iss) -> iss != null && ISSUERS.contains(iss.toString())),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, (aud) -> aud != null && aud.contains(clientId))));
		this.decoder = decoder;
	}

	public GoogleIdentity verify(String idToken) {
		Jwt jwt;
		try {
			jwt = this.decoder.decode(idToken);
		}
		catch (BadJwtException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid-google-token",
					"Google ID token is invalid or expired");
		}
		catch (JwtException ex) {
			// The token may be fine; Google's signing keys couldn't be fetched.
			logger.warn("Couldn't check a Google ID token", ex);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "google-unavailable",
					"Google sign-in can't be checked right now");
		}
		Object emailVerified = jwt.getClaim("email_verified");
		return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"),
				emailVerified != null && Boolean.parseBoolean(emailVerified.toString()), jwt.getClaimAsString("name"));
	}

}
