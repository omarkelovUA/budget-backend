package com.familybudget.family.event;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) when a user joins a
 * family, by accepting an invitation or having their join request approved. A user's own
 * new family (at sign-up, after leaving) doesn't publish it: they're alone in it, with
 * nobody to set anything up with.
 */
public record FamilyMembershipStarted(UUID userId, UUID familyId) {

}
