package com.familybudget.family.event;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) whenever a user stops
 * being a member of a family: leaving, being removed, or moving to another family (an
 * accepted invitation or an approved join request). Leaving or being removed puts them in
 * a new family of their own. Lets other modules drop what only made sense within the family.
 */
public record FamilyMembershipEnded(UUID userId, UUID familyId) {

}
