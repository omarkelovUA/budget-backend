package com.familybudget.subscription.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.familybudget.common.exception.ApiException;

/**
 * A daily safety net behind Google's notifications: re-reads Play purchases that expire
 * soon or already have, in case a notification was lost.
 */
@Component
public class SubscriptionRecheck {

	private static final Logger logger = LoggerFactory.getLogger(SubscriptionRecheck.class);

	private static final Duration WITHIN = Duration.ofDays(3);

	private final SubscriptionService subscriptions;

	SubscriptionRecheck(SubscriptionService subscriptions) {
		this.subscriptions = subscriptions;
	}

	@Scheduled(cron = "${app.subscription.recheck-cron}")
	public void recheckExpiring() {
		for (String token : this.subscriptions.expiringPurchaseTokens(WITHIN)) {
			try {
				this.subscriptions.refreshGooglePlay(token);
			}
			catch (ApiException ex) {
				logger.warn("Couldn't re-check a Google Play subscription: {}", ex.getMessage());
			}
		}
	}

}
