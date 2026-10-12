package com.familybudget.account.model;

import java.util.UUID;

/**
 * How a transaction shows the accounts it touches: no balance, since a transaction can be
 * visible through one account while the caller has no access to the other one
 * ({@code access = none}). {@code inTransit} marks a transient account between family
 * members (PLAN.md M14).
 */
public record AccountRef(UUID id, String name, String currency, String icon, String color, AccountAccess access,
		boolean closed, boolean inTransit) {

}
