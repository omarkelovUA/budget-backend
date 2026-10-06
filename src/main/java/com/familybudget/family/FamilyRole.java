package com.familybudget.family;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Lower-case on the wire, matching the frontend's {@code FamilyRole} type. */
public enum FamilyRole {

	@JsonProperty("admin")
	ADMIN,

	@JsonProperty("member")
	MEMBER

}
