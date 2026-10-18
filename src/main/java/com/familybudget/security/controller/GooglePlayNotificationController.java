package com.familybudget.security.controller;

import java.util.Base64;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import com.familybudget.security.service.GooglePlayPushVerifier;
import com.familybudget.subscription.service.SubscriptionService;

/**
 * Google Play's real-time developer notifications, pushed by Pub/Sub (ARCHITECTURE.md
 * §3.4, §6.1). The message only says which purchase changed; its state is re-read from
 * Play. Anything we don't act on still gets a 2xx, or Pub/Sub would retry it for days.
 */
@RestController
class GooglePlayNotificationController {

	// Play's productType for subscriptions in voided-purchase notifications.
	private static final int SUBSCRIPTION = 1;

	private final GooglePlayPushVerifier verifier;

	private final SubscriptionService subscriptionService;

	private final JsonMapper json;

	private final String packageName;

	GooglePlayNotificationController(GooglePlayPushVerifier verifier, SubscriptionService subscriptionService,
			JsonMapper json, @Value("${app.subscription.google-play.package-name}") String packageName) {
		this.verifier = verifier;
		this.subscriptionService = subscriptionService;
		this.json = json;
		this.packageName = packageName;
	}

	@PostMapping("/api/v1/webhooks/google-play")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void receive(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) @Nullable String authorization,
			@Valid @RequestBody PushRequest push) {
		this.verifier.verify(authorization);
		DeveloperNotification notification = this.json.readValue(Base64.getDecoder().decode(push.message().data()),
				DeveloperNotification.class);
		if (!this.packageName.equals(notification.packageName())) {
			return;
		}
		String token = null;
		if (notification.subscriptionNotification() != null) {
			token = notification.subscriptionNotification().purchaseToken();
		}
		else if (notification.voidedPurchaseNotification() != null
				&& notification.voidedPurchaseNotification().productType() == SUBSCRIPTION) {
			token = notification.voidedPurchaseNotification().purchaseToken();
		}
		if (token != null) {
			this.subscriptionService.refreshGooglePlay(token);
		}
	}

	record PushRequest(@Valid @NotNull Message message) {

		record Message(@NotNull String data) {
		}

	}

	// The parts of Play's DeveloperNotification the backend reads; test notifications have neither.
	record DeveloperNotification(@Nullable String packageName,
			@Nullable SubscriptionNotification subscriptionNotification,
			@Nullable VoidedPurchaseNotification voidedPurchaseNotification) {
	}

	record SubscriptionNotification(String purchaseToken) {
	}

	record VoidedPurchaseNotification(String purchaseToken, int productType) {
	}

}
