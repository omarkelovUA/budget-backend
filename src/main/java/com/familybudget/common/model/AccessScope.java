package com.familybudget.common.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import com.familybudget.common.exception.ApiException;

/**
 * What a user may see and change: their own private data plus their family's shared data
 * (ARCHITECTURE.md §6.3). Built by {@code FamilyService.scopeFor(userId)}. While the
 * family is {@code frozen} (members but no subscription, §3.4) its shared data is
 * read-only.
 */
public record AccessScope(UUID userId, @Nullable UUID familyId, boolean frozen) {

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
		requireWritable(OwnerType.FAMILY);
		return this.familyId;
	}

	/** Refuses changes to the family's shared data while the family is frozen. */
	public void requireWritable(OwnerType ownerType) {
		if (this.frozen && ownerType == OwnerType.FAMILY) {
			throw familyFrozen();
		}
	}

	public static ApiException familyFrozen() {
		return new ApiException(HttpStatus.FORBIDDEN, "family-frozen",
				"Shared data is read-only until a family admin renews the subscription");
	}

	public static OwnerType ownerTypeFor(Visibility visibility) {
		return (visibility == Visibility.SHARED) ? OwnerType.FAMILY : OwnerType.USER;
	}

}
