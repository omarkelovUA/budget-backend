package com.familybudget.family.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code categoriesReviewed}: whether the admin has chosen which categories the family
 * sees; asked once, when the first other member arrives.
 */
public record FamilyView(UUID id, String name, List<Member> members, boolean categoriesReviewed) {

	public record Member(UUID userId, String email, String displayName, FamilyRole role, Instant joinedAt) {
	}

}
