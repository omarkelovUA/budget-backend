package com.familybudget.subscription.model;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * {@code active}: whether it entitles its owner now. {@code expiresAt} is null for a
 * manual grant without an end.
 */
public record SubscriptionView(SubscriptionSource source, SubscriptionState state, @Nullable String productId,
		@Nullable String basePlanId, @Nullable Instant expiresAt, boolean autoRenewing, boolean active) {

}
