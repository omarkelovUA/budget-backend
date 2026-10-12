package com.familybudget.user.model;

import java.util.UUID;

/** Public, credential-free view of a user, safe to return from the API. */
public record UserView(UUID id, String email, String displayName) {

}
