package com.familybudget.family.model;

import java.time.Instant;
import java.util.UUID;

/** A pending invitation as the inviting family sees it. */
public record InvitationView(UUID id, String email, Instant createdAt, Instant expiresAt) {

}
