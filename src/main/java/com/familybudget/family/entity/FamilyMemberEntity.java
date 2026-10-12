package com.familybudget.family.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.familybudget.family.model.FamilyRole;

/** Keyed by user id: the primary key is what enforces "one family per user" (v1). */
@Entity
@Table(name = "family_members")
public class FamilyMemberEntity {

	@Id
	private UUID userId;

	@Column(nullable = false)
	private UUID familyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FamilyRole role;

	@Column(nullable = false)
	private Instant joinedAt;

	protected FamilyMemberEntity() {
	}

	public FamilyMemberEntity(UUID userId, UUID familyId, FamilyRole role) {
		this.userId = userId;
		this.familyId = familyId;
		this.role = role;
		this.joinedAt = Instant.now();
	}

	public UUID getUserId() {
		return this.userId;
	}

	public UUID getFamilyId() {
		return this.familyId;
	}

	public FamilyRole getRole() {
		return this.role;
	}

	public void setRole(FamilyRole role) {
		this.role = role;
	}

	/** Changing family moves the membership row: one family per user. */
	public void moveTo(UUID familyId, FamilyRole role) {
		this.familyId = familyId;
		this.role = role;
		this.joinedAt = Instant.now();
	}

	public Instant getJoinedAt() {
		return this.joinedAt;
	}

}
