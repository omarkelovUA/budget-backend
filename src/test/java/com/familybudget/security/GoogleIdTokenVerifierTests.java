package com.familybudget.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import com.familybudget.TestGoogleIdTokens;
import com.familybudget.common.exception.ApiException;
import com.familybudget.security.model.GoogleIdentity;
import com.familybudget.security.service.GoogleIdTokenVerifier;

/** Google ID tokens sent by the mobile app (ARCHITECTURE.md §6.1). */
class GoogleIdTokenVerifierTests {

	private final GoogleIdTokenVerifier verifier = TestGoogleIdTokens.verifier();

	@Test
	void validTokenGivesTheIdentity() {
		String token = TestGoogleIdTokens.sign((claims) -> claims.subject("google-1")
			.claim("email", "a@example.com")
			.claim("email_verified", true)
			.claim("name", "Ann"));

		assertThat(this.verifier.verify(token)).isEqualTo(new GoogleIdentity("google-1", "a@example.com", true, "Ann"));
	}

	@Test
	void issuerWithoutSchemeIsAccepted() {
		String token = TestGoogleIdTokens.sign((claims) -> claims.issuer("accounts.google.com"));

		assertThat(this.verifier.verify(token).subject()).startsWith("google-");
	}

	@Test
	void missingEmailVerifiedMeansUnverified() {
		String token = TestGoogleIdTokens.sign((claims) -> claims.claims((all) -> all.remove("email_verified")));

		assertThat(this.verifier.verify(token).emailVerified()).isFalse();
	}

	@ParameterizedTest
	@MethodSource("invalidTokens")
	void rejectsInvalidTokens(String token) {
		assertThatThrownBy(() -> this.verifier.verify(token)).isInstanceOfSatisfying(ApiException.class, (ex) -> {
			assertThat(ex.getType()).isEqualTo("invalid-google-token");
			assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
		});
	}

	static Stream<String> invalidTokens() {
		Instant now = Instant.now();
		return Stream.of(
				TestGoogleIdTokens.sign((claims) -> claims.issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600))),
				TestGoogleIdTokens.sign((claims) -> claims.audience(List.of("someone-else"))),
				TestGoogleIdTokens.sign((claims) -> claims.issuer("https://evil.example")),
				TestGoogleIdTokens.signWithForeignKey(), "not-a-jwt");
	}

}
