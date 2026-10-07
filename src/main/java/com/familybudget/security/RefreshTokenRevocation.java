package com.familybudget.security;

import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.user.UserSessionsInvalidated;

/** Ends every refresh-token session of a user whose sessions were invalidated. */
@Component
class RefreshTokenRevocation {

	private final RefreshTokenRepository tokens;

	RefreshTokenRevocation(RefreshTokenRepository tokens) {
		this.tokens = tokens;
	}

	@EventListener
	@Transactional
	void on(UserSessionsInvalidated event) {
		this.tokens.revokeAllForUser(event.userId(), Instant.now());
	}

}
