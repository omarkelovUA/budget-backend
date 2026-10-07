package com.familybudget.family;

import java.time.Instant;
import java.util.UUID;

/** A pending invitation as the invitee sees it. */
public record ReceivedInvitationView(UUID id, UUID familyId, String familyName, String invitedBy, int memberCount,
		Instant expiresAt) {

}
