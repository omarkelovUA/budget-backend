package com.familybudget.user;

import java.util.UUID;

/**
 * Published (synchronously, inside the same database transaction) when a new user signs
 * up, with a password or with Google. The family module gives them their own family.
 */
public record UserRegistered(UUID userId, String language) {

}
