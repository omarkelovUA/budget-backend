package com.familybudget.security.entity;

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
public class RefreshTokenEntity {

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

	public RefreshTokenEntity(UUID userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	@Nullable public Instant getRevokedAt() {
		return this.revokedAt;
	}

	public void revoke(Instant now) {
		this.revokedAt = now;
	}

}
