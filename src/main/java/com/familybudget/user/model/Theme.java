package com.familybudget.user.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The UI theme; {@code SYSTEM} follows the device's light/dark preference. */
public enum Theme {

	LIGHT, DARK, SYSTEM;

	/** As stored and sent over the wire. */
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<Theme> fromCode(String code) {
		return Arrays.stream(values()).filter((theme) -> theme.code().equals(code)).findFirst();
	}

}
