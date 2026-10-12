package com.familybudget.security.service;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
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

	public static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

	// Google issues both spellings.
	private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

	private final NimbusJwtDecoder decoder;

	/** Installs the Google token validator on {@code decoder}. */
	public GoogleIdTokenVerifier(NimbusJwtDecoder decoder, String clientId) {
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
				new JwtClaimValidator<>(JwtClaimNames.ISS, (Object iss) -> iss != null && ISSUERS.contains(iss.toString())),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, (aud) -> aud != null && aud.contains(clientId))));
		this.decoder = decoder;
	}

	public GoogleIdentity verify(String idToken) {
		Jwt jwt;
		try {
			jwt = this.decoder.decode(idToken);
		}
		catch (JwtException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid-google-token",
					"Google ID token is invalid or expired");
		}
		Object emailVerified = jwt.getClaim("email_verified");
		return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"),
				emailVerified != null && Boolean.parseBoolean(emailVerified.toString()), jwt.getClaimAsString("name"));
	}

}
