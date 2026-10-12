/**
 * Families, memberships, email invitations and join requests (ARCHITECTURE.md §3.2). Every
 * user belongs to exactly one family in v1 (their own from sign-up on);
 * {@link com.familybudget.family.service.FamilyService#currentFamilyId} is what later modules use to
 * scope shared data.
 */
@NullMarked
package com.familybudget.family;

import org.jspecify.annotations.NullMarked;
