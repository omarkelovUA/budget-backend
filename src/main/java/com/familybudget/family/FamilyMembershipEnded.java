package com.familybudget.family;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) whenever a user stops
 * being a member of a family: leaving, being removed, or moving to another family by
 * accepting an invitation. Lets other modules drop what only made sense within the family.
 */
public record FamilyMembershipEnded(UUID userId, UUID familyId) {

}
