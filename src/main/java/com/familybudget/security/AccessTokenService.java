package com.familybudget.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.familybudget.user.UserView;

/** Issues the short-lived access JWT that the resource server validates (ARCHITECTURE.md §6.1). */
@Service
class AccessTokenService {

	static final String ISSUER = "familybudget";

	private final JwtEncoder encoder;

	private final Duration ttl;

	AccessTokenService(JwtEncoder encoder, @Value("${app.security.jwt.access-token-ttl}") Duration ttl) {
		this.encoder = encoder;
		this.ttl = ttl;
	}

	AuthResponse issue(UserView user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(user.id().toString())
			.issuedAt(now)
			.expiresAt(now.plus(this.ttl))
			.claim("email", user.email())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AuthResponse(token, "Bearer", this.ttl.toSeconds(), user);
	}

}
