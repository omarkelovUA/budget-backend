package com.familybudget.user.model;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Picks a default currency from the client's preferred locales (its {@code Accept-Language}),
 * in order: the first locale whose country has a currency wins. A locale without a country
 * (e.g. just {@code uk}) stands for its language's main country.
 */
public final class RegionCurrency {

	/** The app's original currency, used when nothing in the locales says otherwise. */
	public static final String FALLBACK = "UAH";

	// Languages spoken mainly in one country. Widely shared ones (en, es, pt, ru, ar...)
	// are left out on purpose: guessing a country for them would be wrong too often.
	private static final Map<String, String> MAIN_COUNTRY = Map.ofEntries(Map.entry("uk", "UA"),
			Map.entry("pl", "PL"), Map.entry("cs", "CZ"), Map.entry("sk", "SK"), Map.entry("hu", "HU"),
			Map.entry("ro", "RO"), Map.entry("bg", "BG"), Map.entry("lt", "LT"), Map.entry("lv", "LV"),
			Map.entry("et", "EE"), Map.entry("da", "DK"), Map.entry("sv", "SE"), Map.entry("nb", "NO"),
			Map.entry("ja", "JP"), Map.entry("ka", "GE"));

	private RegionCurrency() {
	}

	public static String forLocales(List<Locale> locales) {
		for (Locale locale : locales) {
			String country = locale.getCountry().isEmpty() ? MAIN_COUNTRY.get(locale.getLanguage())
					: locale.getCountry();
			@Nullable String currency = (country != null) ? currencyOf(country) : null;
			if (currency != null) {
				return currency;
			}
		}
		return FALLBACK;
	}

	private static @Nullable String currencyOf(String country) {
		try {
			Currency currency = Currency.getInstance(Locale.of("", country));
			return (currency != null) ? currency.getCurrencyCode() : null;
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
