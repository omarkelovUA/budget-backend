package com.familybudget.user;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the UI language from the client's preferred locales (its {@code Accept-Language}),
 * in order: a supported language wins; otherwise a country whose language is supported
 * (e.g. {@code ru-UA} means Ukraine, so Ukrainian); nothing usable means English.
 */
public final class RegionLanguage {

	private static final Map<String, SupportedLanguage> COUNTRY_LANGUAGE = Map.of("UA", SupportedLanguage.UK);

	private RegionLanguage() {
	}

	public static String forLocales(List<Locale> locales) {
		for (Locale locale : locales) {
			Optional<SupportedLanguage> language = SupportedLanguage.fromCode(locale.getLanguage())
				.or(() -> Optional.ofNullable(COUNTRY_LANGUAGE.get(locale.getCountry())));
			if (language.isPresent()) {
				return language.get().code();
			}
		}
		return SupportedLanguage.FALLBACK.code();
	}

}
