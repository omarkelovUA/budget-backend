package com.familybudget.subscription.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.auth.oauth2.GoogleCredentials;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.familybudget.common.exception.ApiException;
import com.familybudget.subscription.model.SubscriptionState;

/**
 * {@link GooglePlayClient} over the Android Publisher REST API, authorized as the
 * configured service account. Without credentials (local dev, tests) every call is
 * {@code 503 google-play-unavailable}; manual grants work regardless.
 */
public class RestGooglePlayClient implements GooglePlayClient {

	private static final Logger logger = LoggerFactory.getLogger(RestGooglePlayClient.class);

	public static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";

	private static final Map<String, SubscriptionState> STATES = Map.of("SUBSCRIPTION_STATE_PENDING",
			SubscriptionState.PENDING, "SUBSCRIPTION_STATE_ACTIVE", SubscriptionState.ACTIVE,
			"SUBSCRIPTION_STATE_IN_GRACE_PERIOD", SubscriptionState.IN_GRACE, "SUBSCRIPTION_STATE_ON_HOLD",
			SubscriptionState.ON_HOLD, "SUBSCRIPTION_STATE_PAUSED", SubscriptionState.PAUSED,
			"SUBSCRIPTION_STATE_CANCELED", SubscriptionState.CANCELED, "SUBSCRIPTION_STATE_EXPIRED",
			SubscriptionState.EXPIRED);

	private final RestClient http;

	private final String packageName;

	private final @Nullable GoogleCredentials credentials;

	public RestGooglePlayClient(RestClient.Builder http, String packageName, @Nullable GoogleCredentials credentials) {
		this.http = http.baseUrl("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + packageName)
			.build();
		this.packageName = packageName;
		this.credentials = credentials;
	}

	@Override
	public Purchase fetch(String purchaseToken) {
		SubscriptionPurchaseV2 purchase = call(() -> this.http.get()
			.uri("/purchases/subscriptionsv2/tokens/{token}", purchaseToken)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.retrieve()
			.body(SubscriptionPurchaseV2.class));
		if (purchase == null || purchase.lineItems() == null || purchase.lineItems().isEmpty()) {
			throw invalidPurchase();
		}
		LineItem item = purchase.lineItems().getFirst();
		Instant expiresAt = purchase.lineItems()
			.stream()
			.map(LineItem::expiryTime)
			.filter(Objects::nonNull)
			.max(Instant::compareTo)
			.orElse(null);
		ExternalAccountIdentifiers account = purchase.externalAccountIdentifiers();
		return new Purchase(STATES.getOrDefault(purchase.subscriptionState(), SubscriptionState.EXPIRED),
				item.productId(), (item.offerDetails() != null) ? item.offerDetails().basePlanId() : null, expiresAt,
				item.autoRenewingPlan() != null && item.autoRenewingPlan().autoRenewEnabled(),
				"ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(purchase.acknowledgementState()),
				(account != null) ? account.obfuscatedExternalAccountId() : null, purchase.linkedPurchaseToken());
	}

	@Override
	public void acknowledge(String productId, String purchaseToken) {
		call(() -> this.http.post()
			.uri("/purchases/subscriptions/{product}/tokens/{token}:acknowledge", productId, purchaseToken)
			.header(HttpHeaders.AUTHORIZATION, bearer())
			.body(Map.of())
			.retrieve()
			.toBodilessEntity());
	}

	private String bearer() {
		if (this.credentials == null) {
			throw unavailable();
		}
		try {
			this.credentials.refreshIfExpired();
			return "Bearer " + this.credentials.getAccessToken().getTokenValue();
		}
		catch (IOException ex) {
			logger.warn("Couldn't get a Google Play API token", ex);
			throw unavailable();
		}
	}

	/** A token Play rejects is the caller's mistake; anything else is Play being unreachable. */
	private <T> T call(PlayCall<T> request) {
		try {
			return request.run();
		}
		catch (HttpClientErrorException ex) {
			HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
			if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.NOT_FOUND || status == HttpStatus.GONE) {
				throw invalidPurchase();
			}
			logger.warn("Google Play refused a call for {}", this.packageName, ex);
			throw unavailable();
		}
		catch (RestClientException ex) {
			logger.warn("Couldn't reach Google Play", ex);
			throw unavailable();
		}
	}

	private static ApiException invalidPurchase() {
		return new ApiException(HttpStatus.BAD_REQUEST, "invalid-purchase", "Google Play doesn't know this purchase");
	}

	private static ApiException unavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "google-play-unavailable",
				"Google Play can't be reached right now");
	}

	@FunctionalInterface
	private interface PlayCall<T> {

		T run();

	}

	// The parts of Play's SubscriptionPurchaseV2 the backend reads; Play adds fields over time.
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SubscriptionPurchaseV2(@Nullable String subscriptionState, @Nullable String acknowledgementState,
			@Nullable String linkedPurchaseToken, @Nullable ExternalAccountIdentifiers externalAccountIdentifiers,
			@Nullable List<LineItem> lineItems) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExternalAccountIdentifiers(@Nullable String obfuscatedExternalAccountId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LineItem(String productId, @Nullable Instant expiryTime, @Nullable AutoRenewingPlan autoRenewingPlan,
			@Nullable OfferDetails offerDetails) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AutoRenewingPlan(boolean autoRenewEnabled) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OfferDetails(@Nullable String basePlanId) {
	}

}
