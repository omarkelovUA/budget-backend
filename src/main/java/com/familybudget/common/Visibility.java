package com.familybudget.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The wire-level view of ownership (PLAN.md open decision #2): the frontend only sees
 * private vs. shared, the backend keeps {@link OwnerType} + owner id.
 */
public enum Visibility {

	@JsonProperty("private")
	PRIVATE,

	@JsonProperty("shared")
	SHARED;

	public static Visibility of(OwnerType ownerType) {
		return (ownerType == OwnerType.FAMILY) ? SHARED : PRIVATE;
	}

}
