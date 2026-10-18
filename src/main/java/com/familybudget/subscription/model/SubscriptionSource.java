package com.familybudget.subscription.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SubscriptionSource {

	@JsonProperty("google_play")
	GOOGLE_PLAY,

	/** Granted by hand (testing, later the operator page); no purchase behind it. */
	@JsonProperty("manual")
	MANUAL

}
