package com.familybudget.user;

import java.util.List;

/**
 * The user's own settings; only ever shown to that user. {@code language} is ISO 639-1,
 * {@code usedCurrencies} the ISO 4217 codes pickers offer, always including
 * {@code defaultCurrency}; {@code theme} is a {@link Theme} code; {@code primaryHue} is the
 * OKLCH hue (0–359) the UI palette derives from.
 */
public record UserSettingsView(String language, String defaultCurrency, List<String> usedCurrencies, String theme,
		int primaryHue) {

}
