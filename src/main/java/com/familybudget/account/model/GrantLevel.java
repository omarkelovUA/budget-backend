package com.familybudget.account.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What the owner gives a family member on their account; a member without a grant is hidden. */
public enum GrantLevel {

	/** Sees the account, its balance and transactions; can't record any. */
	@JsonProperty("visible")
	VISIBLE,

	/** Sees the account and records transactions on it. */
	@JsonProperty("shared")
	SHARED

}
