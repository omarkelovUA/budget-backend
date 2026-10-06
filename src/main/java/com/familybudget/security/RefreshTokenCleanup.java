package com.familybudget.security;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges refresh tokens past their expiry. Revoked-but-unexpired rows are kept on
 * purpose: replaying one of those is what triggers reuse detection.
 */
@Component
class RefreshTokenCleanup {

	private static final Logger logger = LoggerFactory.getLogger(RefreshTokenCleanup.class);

	private final RefreshTokenRepository tokens;

	RefreshTokenCleanup(RefreshTokenRepository tokens) {
		this.tokens = tokens;
	}

	@Scheduled(cron = "${app.security.refresh-token.cleanup-cron}")
	@Transactional
	void purgeExpired() {
		int deleted = this.tokens.deleteExpiredBefore(Instant.now());
		if (deleted > 0) {
			logger.info("Purged {} expired refresh tokens", deleted);
		}
	}

}
