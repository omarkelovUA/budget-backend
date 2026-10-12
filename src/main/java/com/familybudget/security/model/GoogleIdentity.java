package com.familybudget.security.model;

import org.jspecify.annotations.Nullable;

/** Who a verified Google ID token belongs to. */
public record GoogleIdentity(String subject, @Nullable String email, boolean emailVerified, @Nullable String name) {

}
