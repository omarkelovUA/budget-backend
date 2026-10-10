package com.familybudget.category;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.familybudget.common.ApiException;

/**
 * Published (synchronously) before the admin hides a family category, which makes it
 * {@code adminId}'s personal one. A module that holds someone else's data in it (their
 * payments, budgets) refuses by throwing {@link #usedByFamily()}.
 */
public record CategoryHiding(UUID categoryId, UUID adminId) {

	public static ApiException usedByFamily() {
		return new ApiException(HttpStatus.CONFLICT, "category-used-by-family",
				"Others have payments or budgets in this category");
	}

}
