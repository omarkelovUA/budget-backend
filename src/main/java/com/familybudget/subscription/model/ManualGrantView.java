package com.familybudget.subscription.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A manual grant: {@code expiresAt} null for no end; {@code active} whether it entitles now. */
public record ManualGrantView(UUID userId, @Nullable Instant expiresAt, Instant createdAt, boolean active) {

}
