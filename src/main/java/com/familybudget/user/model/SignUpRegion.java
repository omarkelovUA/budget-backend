package com.familybudget.user.model;

import java.util.List;
import java.util.Locale;

/** Settings a new user starts with, derived from the region they sign up from. */
public record SignUpRegion(String defaultCurrency, String language) {

	public static SignUpRegion forLocales(List<Locale> locales) {
		return new SignUpRegion(RegionCurrency.forLocales(locales), RegionLanguage.forLocales(locales));
	}

}
