package com.familybudget.user.model;

import java.util.UUID;

/** The caller, as {@code GET /users/me} shows them; {@code operator}: may open the operator page. */
public record CurrentUserView(UUID id, String email, String displayName, boolean operator) {

}
