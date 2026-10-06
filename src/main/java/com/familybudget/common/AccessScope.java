package com.familybudget.common;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * What a user may see and change: their own private data plus their family's shared data
 * (ARCHITECTURE.md §6.3). Built by {@code FamilyService.scopeFor(userId)}.
 */
public record AccessScope(UUID userId, @Nullable UUID familyId) {

	public boolean canAccess(OwnerType ownerType, UUID ownerId) {
		return switch (ownerType) {
			case USER -> this.userId.equals(ownerId);
			case FAMILY -> ownerId.equals(this.familyId);
		};
	}

	/** The owner id for something created with the given visibility. */
	public UUID ownerIdFor(Visibility visibility) {
		if (visibility == Visibility.PRIVATE) {
			return this.userId;
		}
		if (this.familyId == null) {
			throw new ApiException(HttpStatus.CONFLICT, "no-family", "Join or create a family to share data");
		}
		return this.familyId;
	}

	public static OwnerType ownerTypeFor(Visibility visibility) {
		return (visibility == Visibility.SHARED) ? OwnerType.FAMILY : OwnerType.USER;
	}

}
