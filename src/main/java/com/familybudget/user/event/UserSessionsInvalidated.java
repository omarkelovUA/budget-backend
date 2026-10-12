package com.familybudget.user.event;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) when every existing
 * session of a user must end, so the security module can revoke their refresh tokens.
 * Access tokens are already invalid by then: their session version no longer matches.
 */
public record UserSessionsInvalidated(UUID userId) {

}
