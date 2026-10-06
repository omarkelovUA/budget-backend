package com.familybudget.family;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Keyed by user id: the primary key is what enforces "one family per user" (v1). */
@Entity
@Table(name = "family_members")
class FamilyMemberEntity {

	@Id
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private UUID familyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FamilyRole role;

	@Column(nullable = false, updatable = false)
	private Instant joinedAt;

	protected FamilyMemberEntity() {
	}

	FamilyMemberEntity(UUID userId, UUID familyId, FamilyRole role) {
		this.userId = userId;
		this.familyId = familyId;
		this.role = role;
		this.joinedAt = Instant.now();
	}

	UUID getUserId() {
		return this.userId;
	}

	UUID getFamilyId() {
		return this.familyId;
	}

	FamilyRole getRole() {
		return this.role;
	}

	void setRole(FamilyRole role) {
		this.role = role;
	}

	Instant getJoinedAt() {
		return this.joinedAt;
	}

}
