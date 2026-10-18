package com.familybudget.subscription.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.web.CurrentUserId;
import com.familybudget.subscription.model.SubscriptionView;
import com.familybudget.subscription.service.SubscriptionService;

@RestController
@RequestMapping("/api/v1/subscriptions")
class SubscriptionController {

	private final SubscriptionService subscriptionService;

	SubscriptionController(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	/** 204 when the caller never had a subscription. */
	@GetMapping("/me")
	ResponseEntity<SubscriptionView> mine(@CurrentUserId UUID userId) {
		return this.subscriptionService.current(userId)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/google-play")
	SubscriptionView registerGooglePlay(@CurrentUserId UUID userId, @Valid @RequestBody GooglePlayRequest request) {
		return this.subscriptionService.registerGooglePlay(userId, request.purchaseToken());
	}

	record GooglePlayRequest(@NotBlank String purchaseToken) {
	}

}
