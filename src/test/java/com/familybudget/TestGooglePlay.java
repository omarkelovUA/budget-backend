package com.familybudget;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

import com.familybudget.common.exception.ApiException;
import com.familybudget.subscription.model.SubscriptionState;
import com.familybudget.subscription.service.GooglePlayClient;
import com.familybudget.subscription.service.SubscriptionService;

/**
 * Google Play as the integration tests see it: purchases a test puts in by token, and
 * the tokens acknowledged. One instance serves the whole shared context, so each test
 * uses its own tokens.
 */
public class TestGooglePlay implements GooglePlayClient {

	private final Map<String, Purchase> purchases = new ConcurrentHashMap<>();

	private final Set<String> acknowledged = ConcurrentHashMap.newKeySet();

	private final Set<String> unreachable = ConcurrentHashMap.newKeySet();

	/** A fresh token for a monthly purchase {@code buyer} made, in {@code state}. */
	public String purchase(UUID buyer, SubscriptionState state, @Nullable Instant expiresAt,
			@Nullable String linkedPurchaseToken) {
		String token = "token-" + UUID.randomUUID();
		put(token, buyer, state, expiresAt, linkedPurchaseToken);
		return token;
	}

	/** Sets what Play says about {@code token}, e.g. after a renewal or a cancellation. */
	public void put(String token, UUID buyer, SubscriptionState state, @Nullable Instant expiresAt,
			@Nullable String linkedPurchaseToken) {
		this.purchases.put(token, new Purchase(state, "family_sharing", "monthly", expiresAt, true,
				this.acknowledged.contains(token), SubscriptionService.accountIdFor(buyer), linkedPurchaseToken));
	}

	/** Calls about {@code token} fail as if Play were down. */
	public void makeUnreachable(String token) {
		this.unreachable.add(token);
	}

	public boolean isAcknowledged(String token) {
		return this.acknowledged.contains(token);
	}

	@Override
	public Purchase fetch(String purchaseToken) {
		if (this.unreachable.contains(purchaseToken)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "google-play-unavailable", "Play is down");
		}
		Purchase purchase = this.purchases.get(purchaseToken);
		if (purchase == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-purchase", "Unknown token");
		}
		return purchase;
	}

	@Override
	public void acknowledge(String productId, String purchaseToken) {
		this.acknowledged.add(purchaseToken);
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		TestGooglePlay testGooglePlay() {
			return new TestGooglePlay();
		}

	}

}
