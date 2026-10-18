package com.familybudget.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Date;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.familybudget.common.exception.ApiException;
import com.familybudget.subscription.model.SubscriptionState;

/** Reading Play's SubscriptionPurchaseV2 and its errors (ARCHITECTURE.md §3.4). */
class RestGooglePlayClientTests {

	private static final String BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/app.familybudget";

	private final RestClient.Builder http = RestClient.builder();

	private final MockRestServiceServer play = MockRestServiceServer.bindTo(this.http).build();

	private final GooglePlayClient client = new RestGooglePlayClient(this.http, "app.familybudget",
			GoogleCredentials.create(new AccessToken("service-token", Date.from(Instant.now().plusSeconds(3600)))));

	@Test
	void readsAPurchase() {
		this.play.expect(requestTo(BASE + "/purchases/subscriptionsv2/tokens/t1"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer service-token"))
			.andRespond(withSuccess("""
					{"kind": "androidpublisher#subscriptionPurchaseV2", "regionCode": "UA",
					 "subscriptionState": "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
					 "acknowledgementState": "ACKNOWLEDGEMENT_STATE_PENDING", "linkedPurchaseToken": "t0",
					 "externalAccountIdentifiers": {"obfuscatedExternalAccountId": "abc"},
					 "lineItems": [
					   {"productId": "family_sharing", "expiryTime": "2026-11-18T10:00:00.123Z",
					    "autoRenewingPlan": {"autoRenewEnabled": true, "priceChangeDetails": {}},
					    "offerDetails": {"basePlanId": "monthly", "offerId": "trial", "offerTags": []}},
					   {"productId": "family_sharing", "expiryTime": "2026-12-18T10:00:00Z"}],
					 "testPurchase": {}}
					""", MediaType.APPLICATION_JSON));

		assertThat(this.client.fetch("t1")).isEqualTo(new GooglePlayClient.Purchase(SubscriptionState.IN_GRACE,
				"family_sharing", "monthly", Instant.parse("2026-12-18T10:00:00Z"), true, false, "abc", "t0"));
	}

	@Test
	void anUnknownStateEntitlesNothing() {
		this.play.expect(requestTo(BASE + "/purchases/subscriptionsv2/tokens/t1"))
			.andRespond(withSuccess("""
					{"subscriptionState": "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED",
					 "acknowledgementState": "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
					 "lineItems": [{"productId": "family_sharing"}]}
					""", MediaType.APPLICATION_JSON));

		GooglePlayClient.Purchase purchase = this.client.fetch("t1");

		assertThat(purchase.state()).isEqualTo(SubscriptionState.EXPIRED);
		assertThat(purchase.acknowledged()).isTrue();
		assertThat(purchase.autoRenewing()).isFalse();
		assertThat(purchase.obfuscatedAccountId()).isNull();
	}

	@Test
	void acknowledges() {
		this.play.expect(requestTo(BASE + "/purchases/subscriptions/family_sharing/tokens/t1:acknowledge"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer service-token"))
			.andRespond(withSuccess());

		this.client.acknowledge("family_sharing", "t1");

		this.play.verify();
	}

	@Test
	void aTokenPlayDoesntKnowIsInvalid() {
		this.play.expect(requestTo(BASE + "/purchases/subscriptionsv2/tokens/t1"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> this.client.fetch("t1"))
			.satisfies((ex) -> assertThat(ex.getType()).isEqualTo("invalid-purchase"));
	}

	@Test
	void otherFailuresMeanPlayIsUnavailable() {
		this.play.expect(requestTo(BASE + "/purchases/subscriptionsv2/tokens/t1")).andRespond(withServerError());
		this.play.expect(requestTo(BASE + "/purchases/subscriptionsv2/tokens/t2"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN));
		GooglePlayClient unconfigured = new RestGooglePlayClient(RestClient.builder(), "app.familybudget", null);

		for (Runnable call : new Runnable[] { () -> this.client.fetch("t1"), () -> this.client.fetch("t2"),
				() -> unconfigured.fetch("t3") }) {
			assertThatExceptionOfType(ApiException.class).isThrownBy(call::run)
				.satisfies((ex) -> assertThat(ex.getType()).isEqualTo("google-play-unavailable"));
		}
	}

}
