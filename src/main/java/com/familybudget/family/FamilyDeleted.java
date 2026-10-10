package com.familybudget.family;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) when a family is
 * deleted because its last member moved to another family (an accepted invitation or an
 * approved join request). Modules holding family-owned data hand it to {@code lastMemberId}, the only
 * person who could still see it.
 */
public record FamilyDeleted(UUID familyId, UUID lastMemberId) {

}
