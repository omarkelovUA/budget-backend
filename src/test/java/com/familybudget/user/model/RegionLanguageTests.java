package com.familybudget.user.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class RegionLanguageTests {

	@Test
	void usesTheFirstSupportedLanguage() {
		assertThat(RegionLanguage.forLocales(List.of(Locale.forLanguageTag("uk-UA")))).isEqualTo("uk");
		assertThat(RegionLanguage.forLocales(List.of(Locale.forLanguageTag("en-US")))).isEqualTo("en");
		assertThat(RegionLanguage.forLocales(List.of(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("uk"))))
			.isEqualTo("uk");
	}

	@Test
	void aCountryStandsForItsLanguageWhenTheLanguageIsNotSupported() {
		assertThat(RegionLanguage.forLocales(List.of(Locale.forLanguageTag("ru-UA")))).isEqualTo("uk");
	}

	@Test
	void fallsBackToEnglish() {
		assertThat(RegionLanguage.forLocales(List.of())).isEqualTo("en");
		assertThat(RegionLanguage.forLocales(List.of(Locale.forLanguageTag("de-DE")))).isEqualTo("en");
		assertThat(RegionLanguage.forLocales(List.of(Locale.forLanguageTag("pl")))).isEqualTo("en");
	}

}
