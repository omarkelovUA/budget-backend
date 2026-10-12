package com.familybudget.user.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class RegionCurrencyTests {

	@Test
	void usesTheCountryOfTheFirstLocaleThatHasOne() {
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("uk-UA")))).isEqualTo("UAH");
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("en-US")))).isEqualTo("USD");
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("de-AT")))).isEqualTo("EUR");
	}

	@Test
	void aLanguageAloneStandsForItsMainCountryWhenThereIsOne() {
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("pl")))).isEqualTo("PLN");
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("uk")))).isEqualTo("UAH");
		// "en" alone could be anywhere, so the next preference decides.
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("cs-CZ"))))
			.isEqualTo("CZK");
	}

	@Test
	void fallsBackToHryvnia() {
		assertThat(RegionCurrency.forLocales(List.of())).isEqualTo("UAH");
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("en")))).isEqualTo("UAH");
		assertThat(RegionCurrency.forLocales(List.of(Locale.forLanguageTag("en-AQ")))).isEqualTo("UAH");
	}

}
