package com.familybudget.family.entity;

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

import com.familybudget.family.model.InvitationStatus;

/**
 * An invitation addressed to an email. Whoever signs in with that (Google-verified) email
 * can accept it; there's no email delivery, the invitee sees it in the app.
 */
@Entity
@Table(name = "family_invitations")
public class FamilyInvitationEntity {

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

	public FamilyInvitationEntity(UUID familyId, String email, UUID invitedBy, Instant expiresAt) {
		this.familyId = familyId;
		this.email = email;
		this.invitedBy = invitedBy;
		this.status = InvitationStatus.PENDING;
		this.createdAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	public UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Invitation has not been persisted yet");
		}
		return this.id;
	}

	public UUID getFamilyId() {
		return this.familyId;
	}

	public String getEmail() {
		return this.email;
	}

	public UUID getInvitedBy() {
		return this.invitedBy;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public boolean isOpen(Instant now) {
		return this.status == InvitationStatus.PENDING && this.expiresAt.isAfter(now);
	}

	public void close(InvitationStatus status, Instant now) {
		this.status = status;
		this.respondedAt = now;
	}

}
