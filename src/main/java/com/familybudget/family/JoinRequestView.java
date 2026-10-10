package com.familybudget.family;

import java.time.Instant;
import java.util.UUID;

/** The caller's own open join request: whose family they asked to join. */
public record JoinRequestView(UUID id, String email, String familyName, Instant createdAt, Instant expiresAt) {

}
