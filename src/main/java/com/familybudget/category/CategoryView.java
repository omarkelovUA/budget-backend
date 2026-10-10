package com.familybudget.category;

import java.util.UUID;

import com.familybudget.common.Visibility;

/**
 * {@code unknown}: someone else's personal category, seen through one of their payments
 * the viewer may see; only its id and type are given, so it stays private.
 */
public record CategoryView(UUID id, CategoryType type, String name, String icon, String color, Visibility visibility,
		boolean unknown) {

	static CategoryView unknown(UUID id, CategoryType type) {
		return new CategoryView(id, type, "", "", "", Visibility.PRIVATE, true);
	}

}
