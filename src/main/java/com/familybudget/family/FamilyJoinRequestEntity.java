package com.familybudget.family;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * A user alone in their family asking to join the family of the person whose email they
 * gave. That family's admins answer it. Its status reads like an invitation's: ACCEPTED
 * means approved, REVOKED withdrawn by the requester (or obsolete once their family grew).
 */
@Entity
@Table(name = "family_join_requests")
class FamilyJoinRequestEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Column(nullable = false, updatable = false)
	private UUID requesterId;

	@Column(nullable = false, updatable = false)
	private UUID familyId;

	@Column(nullable = false, updatable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	private @Nullable Instant respondedAt;

	protected FamilyJoinRequestEntity() {
	}

	FamilyJoinRequestEntity(UUID requesterId, UUID familyId, String email, Instant expiresAt) {
		this.requesterId = requesterId;
		this.familyId = familyId;
		this.email = email;
		this.status = InvitationStatus.PENDING;
		this.createdAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Join request has not been persisted yet");
		}
		return this.id;
	}

	UUID getRequesterId() {
		return this.requesterId;
	}

	UUID getFamilyId() {
		return this.familyId;
	}

	String getEmail() {
		return this.email;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

	Instant getExpiresAt() {
		return this.expiresAt;
	}

	boolean isOpen(Instant now) {
		return this.status == InvitationStatus.PENDING && this.expiresAt.isAfter(now);
	}

	void close(InvitationStatus status, Instant now) {
		this.status = status;
		this.respondedAt = now;
	}

}
