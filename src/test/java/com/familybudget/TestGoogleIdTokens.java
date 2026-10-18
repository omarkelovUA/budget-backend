package com.familybudget;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.familybudget.security.service.GoogleIdTokenVerifier;
import com.familybudget.security.service.GooglePlayPushVerifier;

/**
 * Google ID tokens (and Pub/Sub's push tokens, which are the same kind) signed with a
 * local RSA key instead of Google's, plus verifiers that trust that key. {@link Config} puts the verifier into every integration test.
 */
public final class TestGoogleIdTokens {

	public static final String CLIENT_ID = "test-google-client";

	/** What Pub/Sub's push tokens for Google Play's notifications are issued for. */
	public static final String PUSH_AUDIENCE = "https://test.familybudget.app/api/v1/webhooks/google-play";

	public static final String PUSH_SERVICE_ACCOUNT = "play-push@test-project.iam.gserviceaccount.com";

	private static final KeyPair KEYS = generateKeyPair();

	private static final KeyPair FOREIGN_KEYS = generateKeyPair();

	private TestGoogleIdTokens() {
	}

	/** A valid token for a new Google user; {@code customizer} overrides any claim. */
	public static String sign(Consumer<JwtClaimsSet.Builder> customizer) {
		return sign(KEYS, customizer);
	}

	/** A valid Pub/Sub push token; {@code customizer} overrides any claim. */
	public static String signPush(Consumer<JwtClaimsSet.Builder> customizer) {
		return sign(KEYS, (claims) -> {
			claims.audience(List.of(PUSH_AUDIENCE)).claim("email", PUSH_SERVICE_ACCOUNT).claims((all) -> all.remove("name"));
			customizer.accept(claims);
		});
	}

	/** Valid claims, but not signed by the trusted key. */
	public static String signWithForeignKey() {
		return sign(FOREIGN_KEYS, (claims) -> {
		});
	}

	public static GoogleIdTokenVerifier verifier() {
		return new GoogleIdTokenVerifier(NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build(),
				CLIENT_ID);
	}

	private static String sign(KeyPair keys, Consumer<JwtClaimsSet.Builder> customizer) {
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer("https://accounts.google.com")
			.audience(List.of(CLIENT_ID))
			.issuedAt(now)
			.expiresAt(now.plusSeconds(3600))
			.subject("google-" + UUID.randomUUID())
			.claim("email", TestUsers.uniqueEmail())
			.claim("email_verified", true)
			.claim("name", "Google User");
		customizer.accept(claims);
		NimbusJwtEncoder encoder = NimbusJwtEncoder
			.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate())
			.build();
		return encoder
			.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
			.getTokenValue();
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		GoogleIdTokenVerifier testGoogleIdTokenVerifier() {
			return verifier();
		}

		@Bean
		@Primary
		GooglePlayPushVerifier testGooglePlayPushVerifier() {
			return new GooglePlayPushVerifier(new GoogleIdTokenVerifier(
					NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build(), PUSH_AUDIENCE),
					PUSH_SERVICE_ACCOUNT);
		}

	}

}
