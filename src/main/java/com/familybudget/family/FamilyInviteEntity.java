package com.familybudget.family;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/** A single-use invite code an admin shares out of band (no email delivery in v1). */
@Entity
@Table(name = "family_invites")
class FamilyInviteEntity {

	@Id
	private String code;

	@Column(nullable = false, updatable = false)
	private UUID familyId;

	@Column(nullable = false, updatable = false)
	private UUID createdBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	private @Nullable UUID usedBy;

	private @Nullable Instant usedAt;

	protected FamilyInviteEntity() {
	}

	FamilyInviteEntity(String code, UUID familyId, UUID createdBy, Instant expiresAt) {
		this.code = code;
		this.familyId = familyId;
		this.createdBy = createdBy;
		this.createdAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	String getCode() {
		return this.code;
	}

	UUID getFamilyId() {
		return this.familyId;
	}

	Instant getExpiresAt() {
		return this.expiresAt;
	}

	boolean isUsable(Instant now) {
		return this.usedBy == null && this.expiresAt.isAfter(now);
	}

	void markUsed(UUID userId, Instant now) {
		this.usedBy = userId;
		this.usedAt = now;
	}

}
