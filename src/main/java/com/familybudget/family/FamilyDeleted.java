package com.familybudget.family;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) when a family is
 * deleted because its last member left it, directly or by accepting an invitation
 * elsewhere. Modules holding family-owned data hand it to {@code lastMemberId}, the only
 * person who could still see it.
 */
public record FamilyDeleted(UUID familyId, UUID lastMemberId) {

}
