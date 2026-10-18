package com.familybudget.family.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code categoriesReviewed}: whether the admin has chosen which categories the family
 * sees; asked once, when the first other member arrives. {@code entitled}: an admin has a
 * subscription, so members can be added. {@code frozen}: members but no subscription, so
 * shared data is read-only (ARCHITECTURE.md §3.4).
 */
public record FamilyView(UUID id, String name, List<Member> members, boolean categoriesReviewed, boolean entitled,
		boolean frozen) {

	public record Member(UUID userId, String email, String displayName, FamilyRole role, Instant joinedAt) {
	}

}
