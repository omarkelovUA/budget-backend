/**
 * Families, memberships and invite codes (ARCHITECTURE.md §3.2). A user belongs to at most
 * one family in v1; {@link com.familybudget.family.FamilyService#currentFamilyId} is what
 * later modules use to scope shared data.
 */
@NullMarked
package com.familybudget.family;

import org.jspecify.annotations.NullMarked;
