package com.familybudget.operator.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A manual grant with who has it; {@code expiresAt} null for no end. */
public record GrantView(UUID userId, String email, String displayName, @Nullable Instant expiresAt, Instant createdAt,
		boolean active) {

}
