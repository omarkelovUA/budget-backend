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
 * An invitation addressed to an email. Whoever signs in with that (Google-verified) email
 * can accept it; there's no email delivery, the invitee sees it in the app.
 */
@Entity
@Table(name = "family_invitations")
class FamilyInvitationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Column(nullable = false, updatable = false)
	private UUID familyId;

	@Column(nullable = false, updatable = false)
	private String email;

	@Column(nullable = false, updatable = false)
	private UUID invitedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	private @Nullable Instant respondedAt;

	protected FamilyInvitationEntity() {
	}

	FamilyInvitationEntity(UUID familyId, String email, UUID invitedBy, Instant expiresAt) {
		this.familyId = familyId;
		this.email = email;
		this.invitedBy = invitedBy;
		this.status = InvitationStatus.PENDING;
		this.createdAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Invitation has not been persisted yet");
		}
		return this.id;
	}

	UUID getFamilyId() {
		return this.familyId;
	}

	String getEmail() {
		return this.email;
	}

	UUID getInvitedBy() {
		return this.invitedBy;
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
