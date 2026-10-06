package com.familybudget.family;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FamilyView(UUID id, String name, List<Member> members) {

	public record Member(UUID userId, String email, String displayName, FamilyRole role, Instant joinedAt) {
	}

}
