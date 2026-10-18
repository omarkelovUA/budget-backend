package com.familybudget.subscription.model;

import java.util.Map;

/**
 * Subscriptions for the operator page: users entitled now through Play and through manual
 * grants, and Play purchases by state (any age).
 */
public record SubscriptionStatsView(long activeGooglePlay, long activeManual,
		Map<SubscriptionState, Long> googlePlayByState) {

}
