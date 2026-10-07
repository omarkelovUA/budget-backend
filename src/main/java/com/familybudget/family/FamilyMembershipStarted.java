package com.familybudget.family;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) when a user joins a
 * family by accepting an invitation. Creating a family doesn't publish it: its creator
 * is alone, with nobody to set anything up with.
 */
public record FamilyMembershipStarted(UUID userId, UUID familyId) {

}
