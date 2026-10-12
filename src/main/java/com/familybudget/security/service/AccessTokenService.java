package com.familybudget.security.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.familybudget.security.model.AuthResponse;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

/** Issues the short-lived access JWT that the resource server validates (ARCHITECTURE.md §6.1). */
@Service
public class AccessTokenService {

	public static final String ISSUER = "familybudget";

	/** The user's session version (see {@link SessionVersionValidator}). */
	static final String SESSION_VERSION_CLAIM = "sv";

	private final JwtEncoder encoder;

	private final UserService userService;

	private final Duration ttl;

	AccessTokenService(JwtEncoder encoder, UserService userService,
			@Value("${app.security.jwt.access-token-ttl}") Duration ttl) {
		this.encoder = encoder;
		this.userService = userService;
		this.ttl = ttl;
	}

	public AuthResponse issue(UserView user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(user.id().toString())
			.issuedAt(now)
			.expiresAt(now.plus(this.ttl))
			.claim("email", user.email())
			.claim(SESSION_VERSION_CLAIM, this.userService.sessionVersion(user.id()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AuthResponse(token, "Bearer", this.ttl.toSeconds(), user);
	}

}
