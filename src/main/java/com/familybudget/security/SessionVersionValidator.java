package com.familybudget.security;

import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.familybudget.user.UserService;

/**
 * Rejects access tokens issued before the user's sessions were invalidated (their
 * {@code sv} claim is older than the stored version). Costs one primary-key lookup per
 * request; without it, an ended session would live on until the token's 15-minute expiry.
 */
class SessionVersionValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error STALE_SESSION = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"This session has ended", null);

	private final UserService userService;

	SessionVersionValidator(UserService userService) {
		this.userService = userService;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		Object claim = token.getClaim(AccessTokenService.SESSION_VERSION_CLAIM);
		if (!(claim instanceof Number version)) {
			return OAuth2TokenValidatorResult.failure(STALE_SESSION);
		}
		UUID userId;
		try {
			userId = UUID.fromString(token.getSubject());
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return OAuth2TokenValidatorResult.failure(STALE_SESSION);
		}
		return (version.intValue() == this.userService.sessionVersion(userId)) ? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(STALE_SESSION);
	}

}
