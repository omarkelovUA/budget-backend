package com.familybudget.family;

import java.time.Instant;
import java.util.UUID;

/** An open request to join the caller's family, as its admins see it. */
public record IncomingJoinRequestView(UUID id, UUID requesterId, String displayName, String email,
		Instant createdAt, Instant expiresAt) {

}
