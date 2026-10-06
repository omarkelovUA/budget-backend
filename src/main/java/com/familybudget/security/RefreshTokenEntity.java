package com.familybudget.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

/** A refresh token, stored only as a SHA-256 hash of the value sent in the cookie. */
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private String tokenHash;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	private @Nullable Instant revokedAt;

	protected RefreshTokenEntity() {
	}

	RefreshTokenEntity(UUID userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	UUID getUserId() {
		return this.userId;
	}

	Instant getExpiresAt() {
		return this.expiresAt;
	}

	@Nullable Instant getRevokedAt() {
		return this.revokedAt;
	}

	void revoke(Instant now) {
		this.revokedAt = now;
	}

}
