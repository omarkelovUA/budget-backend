package com.familybudget.subscription.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Google Play's subscription states, lower-case on the wire. */
public enum SubscriptionState {

	/** Bought, but the payment hasn't gone through yet (e.g. cash at a store). */
	@JsonProperty("pending")
	PENDING,

	@JsonProperty("active")
	ACTIVE,

	/** A renewal payment failed; Google keeps it entitled while it retries. */
	@JsonProperty("in_grace")
	IN_GRACE,

	/** The grace period ran out without payment; no longer entitled. */
	@JsonProperty("on_hold")
	ON_HOLD,

	@JsonProperty("paused")
	PAUSED,

	/** Won't renew, but entitled until it expires. */
	@JsonProperty("canceled")
	CANCELED,

	@JsonProperty("expired")
	EXPIRED;

	/** Entitles its owner while not past its expiry. */
	public boolean entitles() {
		return this == ACTIVE || this == IN_GRACE || this == CANCELED;
	}

}
