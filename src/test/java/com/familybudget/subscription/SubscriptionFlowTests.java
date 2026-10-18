package com.familybudget.subscription;

import static com.familybudget.TestApi.PROBLEMS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestApi;
import com.familybudget.TestGoogleIdTokens;
import com.familybudget.TestGooglePlay;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;
import com.familybudget.subscription.model.SubscriptionState;
import com.familybudget.subscription.service.SubscriptionRecheck;

/** Play purchases, Google's notifications and manual grants (ARCHITECTURE.md §3.4). */
@IntegrationTest
class SubscriptionFlowTests {

	private static final Instant NEXT_MONTH = Instant.now().plus(Duration.ofDays(30));

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private final TestApi api;

	private final TestGooglePlay play;

	private final SubscriptionRecheck recheck;

	@Autowired
	SubscriptionFlowTests(MockMvcTester mvc, JdbcTemplate jdbc, TestGooglePlay play, SubscriptionRecheck recheck) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = new TestApi(mvc, jdbc);
		this.play = play;
		this.recheck = recheck;
	}

	@Test
	void aPurchaseIsRegisteredAndAcknowledged() {
		Session alice = TestUsers.register(this.mvc);
		assertThat(mine(alice)).hasStatus(HttpStatus.NO_CONTENT);
		String token = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);

		MvcTestResult registered = register(alice, token);

		assertThat(registered).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"source": "google_play", "state": "active", "productId": "family_sharing", "basePlanId": "monthly",
				 "autoRenewing": true, "active": true}
				""");
		assertThat(this.play.isAcknowledged(token)).isTrue();
		assertThat(mine(alice)).bodyJson().extractingPath("$.active").isEqualTo(true);
		// The app sends it again after a restart: same row, nothing new.
		assertThat(register(alice, token)).hasStatusOk();
		assertThat(rows(alice)).isEqualTo(1);
	}

	@Test
	void someoneElsesPurchaseCantBeClaimed() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		String token = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);

		assertProblem(register(bob, token), HttpStatus.BAD_REQUEST, "invalid-purchase");
		assertThat(register(alice, token)).hasStatusOk();
		// Even if Play were to name bob now, the token stays alice's.
		this.play.put(token, bob.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);
		assertProblem(register(bob, token), HttpStatus.BAD_REQUEST, "invalid-purchase");
		assertThat(mine(bob)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void unknownTokensAndAnUnreachablePlayAreRefused() {
		Session alice = TestUsers.register(this.mvc);
		String token = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);
		this.play.makeUnreachable(token);

		assertProblem(register(alice, "never-bought"), HttpStatus.BAD_REQUEST, "invalid-purchase");
		assertProblem(register(alice, token), HttpStatus.SERVICE_UNAVAILABLE, "google-play-unavailable");
		assertThat(register(alice, " ")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void aPendingPurchaseIsKeptButNeitherAcknowledgedNorActive() {
		Session alice = TestUsers.register(this.mvc);
		String token = this.play.purchase(alice.userId(), SubscriptionState.PENDING, null, null);

		assertThat(register(alice, token)).bodyJson().extractingPath("$.active").isEqualTo(false);
		assertThat(this.play.isAcknowledged(token)).isFalse();
	}

	@Test
	void aNewPurchaseReplacesTheOneItIsLinkedTo() {
		Session alice = TestUsers.register(this.mvc);
		String monthly = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);
		register(alice, monthly);
		String yearly = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE,
				Instant.now().plus(Duration.ofDays(365)), monthly);

		assertThat(register(alice, yearly)).hasStatusOk();

		assertThat(state(monthly)).isEqualTo("EXPIRED");
		assertThat(mine(alice)).bodyJson().extractingPath("$.state").isEqualTo("active");
	}

	@Test
	void aNotificationMakesThePurchaseBeReadAgain() {
		Session alice = TestUsers.register(this.mvc);
		String token = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);
		register(alice, token);
		this.play.put(token, alice.userId(), SubscriptionState.EXPIRED, Instant.now().minusSeconds(60), null);

		assertThat(push(TestGoogleIdTokens.signPush((claims) -> {
		}), subscriptionNotification(token))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mine(alice)).bodyJson().isLenientlyEqualTo("""
				{"state": "expired", "active": false}
				""");
	}

	@Test
	void aRefundedSubscriptionIsReadAgain() {
		Session alice = TestUsers.register(this.mvc);
		String token = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);
		register(alice, token);
		this.play.put(token, alice.userId(), SubscriptionState.EXPIRED, Instant.now().minusSeconds(60), null);

		assertThat(push(TestGoogleIdTokens.signPush((claims) -> {
		}), """
				{"version": "1.0", "packageName": "app.familybudget", "eventTimeMillis": "1",
				 "voidedPurchaseNotification": {"purchaseToken": "%s", "orderId": "GPA.1", "productType": 1, "refundType": 1}}
				""".formatted(token))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mine(alice)).bodyJson().extractingPath("$.active").isEqualTo(false);
	}

	@Test
	void notificationsOnlyComeFromOurPushSubscription() {
		String body = subscriptionNotification("whatever");

		assertThat(push(null, body)).hasStatus(HttpStatus.UNAUTHORIZED);
		for (String token : List.of(TestGoogleIdTokens.signWithForeignKey(),
				TestGoogleIdTokens.signPush((claims) -> claims.audience(List.of("https://elsewhere.example"))),
				TestGoogleIdTokens.signPush((claims) -> claims.claim("email", "someone@evil.example")),
				TestGoogleIdTokens.signPush((claims) -> claims.claim("email_verified", false)),
				// A user's sign-in token isn't a push token.
				TestGoogleIdTokens.sign((claims) -> {
				}))) {
			assertProblem(push(token, body), HttpStatus.UNAUTHORIZED, "unauthenticated");
		}
	}

	@Test
	void notificationsWithNothingToDoAreStillAccepted() {
		String token = TestGoogleIdTokens.signPush((claims) -> {
		});

		for (String body : List.of(subscriptionNotification("never-registered"), """
				{"version": "1.0", "packageName": "app.familybudget", "eventTimeMillis": "1", "testNotification": {"version": "1.0"}}
				""", """
				{"version": "1.0", "packageName": "some.other.app", "eventTimeMillis": "1",
				 "subscriptionNotification": {"version": "1.0", "notificationType": 4, "purchaseToken": "x", "subscriptionId": "y"}}
				""")) {
			assertThat(push(token, body)).hasStatus(HttpStatus.NO_CONTENT);
		}
	}

	@Test
	void theDailyRecheckReadsPurchasesNearTheirExpiry() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		String expiring = this.play.purchase(alice.userId(), SubscriptionState.ACTIVE,
				Instant.now().plus(Duration.ofDays(1)), null);
		String later = this.play.purchase(bob.userId(), SubscriptionState.ACTIVE, NEXT_MONTH, null);
		register(alice, expiring);
		register(bob, later);
		this.play.put(expiring, alice.userId(), SubscriptionState.CANCELED, Instant.now().plus(Duration.ofDays(1)),
				null);
		this.play.put(later, bob.userId(), SubscriptionState.CANCELED, NEXT_MONTH, null);

		this.recheck.recheckExpiring();

		assertThat(state(expiring)).isEqualTo("CANCELED");
		assertThat(state(later)).isEqualTo("ACTIVE");
	}

	@Test
	void aManualGrantEntitlesUntilItsEnd() {
		Session alice = TestUsers.register(this.mvc);

		this.api.subscribe(alice);

		assertThat(mine(alice)).bodyJson().isLenientlyEqualTo("""
				{"source": "manual", "state": "active", "expiresAt": null, "active": true}
				""");
		this.jdbc.update("UPDATE subscriptions SET expires_at = now() - interval '1 minute' WHERE user_id = ?",
				alice.userId());
		assertThat(mine(alice)).bodyJson().extractingPath("$.active").isEqualTo(false);
	}

	private MvcTestResult register(Session session, String token) {
		return this.api.post(session, "/api/v1/subscriptions/google-play", """
				{"purchaseToken": "%s"}
				""".formatted(token));
	}

	private MvcTestResult mine(Session session) {
		return this.api.get(session, "/api/v1/subscriptions/me");
	}

	private MvcTestResult push(@Nullable String token, String notification) {
		var request = this.mvc.post()
			.uri("/api/v1/webhooks/google-play")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"message": {"data": "%s", "messageId": "1", "publishTime": "2026-10-18T10:00:00Z"},
					 "subscription": "projects/test/subscriptions/play"}
					""".formatted(Base64.getEncoder().encodeToString(notification.getBytes(StandardCharsets.UTF_8))));
		if (token != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		return request.exchange();
	}

	private static String subscriptionNotification(String token) {
		return """
				{"version": "1.0", "packageName": "app.familybudget", "eventTimeMillis": "1",
				 "subscriptionNotification": {"version": "1.0", "notificationType": 13, "purchaseToken": "%s",
				  "subscriptionId": "family_sharing"}}
				""".formatted(token);
	}

	private String state(String token) {
		return this.jdbc.queryForObject("SELECT state FROM subscriptions WHERE purchase_token = ?", String.class,
				token);
	}

	private Integer rows(Session session) {
		return this.jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id = ?", Integer.class,
				session.userId());
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
