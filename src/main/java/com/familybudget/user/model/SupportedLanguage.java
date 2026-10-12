package com.familybudget.user.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Languages the UI is translated into. Adding one here (plus its translation file in the
 * frontend) is all it takes; the database only checks the code's shape.
 */
public enum SupportedLanguage {

	EN("en"), UK("uk");

	/** For new users whose region doesn't name a supported language. */
	public static final SupportedLanguage FALLBACK = EN;

	private final String code;

	SupportedLanguage(String code) {
		this.code = code;
	}

	/** ISO 639-1, as stored and sent over the wire. */
	public String code() {
		return this.code;
	}

	public static Optional<SupportedLanguage> fromCode(String code) {
		return Arrays.stream(values()).filter((language) -> language.code.equals(code)).findFirst();
	}

}
