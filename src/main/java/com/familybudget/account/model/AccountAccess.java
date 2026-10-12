package com.familybudget.account.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The caller's access to an account (ARCHITECTURE.md §3.2). */
public enum AccountAccess {

	@JsonProperty("owner")
	OWNER,

	@JsonProperty("shared")
	SHARED,

	@JsonProperty("visible")
	VISIBLE,

	/** Only seen as the other side of a transaction the caller can see. */
	@JsonProperty("none")
	NONE;

	/** May record transactions on the account. */
	public boolean canUse() {
		return this == OWNER || this == SHARED;
	}

	public boolean canView() {
		return this != NONE;
	}

}
