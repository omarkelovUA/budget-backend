package com.familybudget.security.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.exception.ApiException;
import com.familybudget.security.entity.RefreshTokenEntity;
import com.familybudget.security.repository.RefreshTokenRepository;
import com.familybudget.user.service.UserService;

/**
 * Opaque, rotating refresh tokens (ARCHITECTURE.md §6.2). Every refresh revokes the
 * presented token and issues a new one. Presenting an already-revoked token outside a
 * short grace window is treated as theft and revokes all of that user's sessions.
 */
@Service
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository tokens;

	private final UserService userService;

	private final Duration ttl;

	private final Duration reuseGrace;

	RefreshTokenService(RefreshTokenRepository tokens, UserService userService,
			@Value("${app.security.refresh-token.ttl}") Duration ttl,
			@Value("${app.security.refresh-token.reuse-grace}") Duration reuseGrace) {
		this.tokens = tokens;
		this.userService = userService;
		this.ttl = ttl;
		this.reuseGrace = reuseGrace;
	}

	public Duration getTtl() {
		return this.ttl;
	}

	/**
	 * Returns the raw token value; only its hash is stored. Every sign-in and refresh comes
	 * through here, so it also counts the user active today (operator page).
	 */
	@Transactional
	public String issue(UUID userId) {
		this.userService.recordActivity(userId);
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		this.tokens.save(new RefreshTokenEntity(userId, hash(token), Instant.now().plus(this.ttl)));
		return token;
	}

	// Revocations from reuse detection must be committed even though we then reject the request.
	@Transactional(noRollbackFor = ApiException.class)
	public Rotation rotate(String token) {
		Instant now = Instant.now();
		RefreshTokenEntity entity = this.tokens.findByTokenHash(hash(token)).orElseThrow(RefreshTokenService::invalid);
		Instant revokedAt = entity.getRevokedAt();
		if (revokedAt != null) {
			// Two tabs refreshing at once is benign; a stale token replayed later is not.
			if (revokedAt.plus(this.reuseGrace).isBefore(now)) {
				this.tokens.revokeAllForUser(entity.getUserId(), now);
			}
			throw invalid();
		}
		if (entity.getExpiresAt().isBefore(now)) {
			throw invalid();
		}
		entity.revoke(now);
		return new Rotation(entity.getUserId(), issue(entity.getUserId()));
	}

	@Transactional
	public void revoke(String token) {
		this.tokens.findByTokenHash(hash(token)).ifPresent((entity) -> {
			if (entity.getRevokedAt() == null) {
				entity.revoke(Instant.now());
			}
		});
	}

	private static ApiException invalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "invalid-refresh-token", "Refresh token is invalid or expired");
	}

	private static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public record Rotation(UUID userId, String token) {
	}

}
