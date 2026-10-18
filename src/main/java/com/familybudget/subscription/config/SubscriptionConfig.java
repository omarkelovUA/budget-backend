package com.familybudget.subscription.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.familybudget.subscription.service.GooglePlayClient;
import com.familybudget.subscription.service.RestGooglePlayClient;

@Configuration(proxyBeanMethods = false)
class SubscriptionConfig {

	/** {@code credentials}: path to the service account's JSON key; empty disables Play calls. */
	@Bean
	GooglePlayClient googlePlayClient(@Value("${app.subscription.google-play.package-name}") String packageName,
			@Value("${app.subscription.google-play.credentials}") String credentials) throws IOException {
		if (credentials.isBlank()) {
			return new RestGooglePlayClient(RestClient.builder(), packageName, null);
		}
		try (InputStream key = Files.newInputStream(Path.of(credentials))) {
			return new RestGooglePlayClient(RestClient.builder(), packageName,
					GoogleCredentials.fromStream(key).createScoped(RestGooglePlayClient.SCOPE));
		}
	}

}
