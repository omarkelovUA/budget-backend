package com.familybudget.account.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/** {@code grants} is only sent to the owner; other members just see their own {@code access}. */
public record AccountView(UUID id, String name, BigDecimal balance, String currency, String icon, String color,
		Owner owner, AccountAccess access, @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<Grant> grants,
		boolean closed) {

	public record Owner(UUID id, String displayName) {
	}

	public record Grant(UUID userId, String displayName, GrantLevel level) {
	}

}
