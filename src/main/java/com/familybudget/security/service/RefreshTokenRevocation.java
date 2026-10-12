package com.familybudget.security.service;

import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.security.repository.RefreshTokenRepository;
import com.familybudget.user.event.UserSessionsInvalidated;

/** Ends every refresh-token session of a user whose sessions were invalidated. */
@Component
public class RefreshTokenRevocation {

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
