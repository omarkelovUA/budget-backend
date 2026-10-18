package com.familybudget.subscription.service;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.familybudget.subscription.model.SubscriptionState;

/**
 * The Play Developer API calls the backend needs (ARCHITECTURE.md §3.4). Failures are
 * {@code ApiException}s: {@code 400 invalid-purchase} for a token Play doesn't know,
 * {@code 503 google-play-unavailable} when Play can't be asked.
 */
public interface GooglePlayClient {

	Purchase fetch(String purchaseToken);

	void acknowledge(String productId, String purchaseToken);

	/**
	 * {@code obfuscatedAccountId}: what the app set when buying, see
	 * {@link SubscriptionService#accountIdFor}. {@code linkedPurchaseToken}: the purchase
	 * this one replaces, if any.
	 */
	record Purchase(SubscriptionState state, String productId, @Nullable String basePlanId,
			@Nullable Instant expiresAt, boolean autoRenewing, boolean acknowledged,
			@Nullable String obfuscatedAccountId, @Nullable String linkedPurchaseToken) {
	}

}
