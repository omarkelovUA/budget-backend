package com.familybudget.subscription.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.exception.ApiException;
import com.familybudget.subscription.entity.SubscriptionEntity;
import com.familybudget.subscription.model.SubscriptionSource;
import com.familybudget.subscription.model.SubscriptionState;
import com.familybudget.subscription.model.SubscriptionView;
import com.familybudget.subscription.repository.SubscriptionRepository;

/** Public API of the subscription module (ARCHITECTURE.md §3.4). */
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

	private static final List<SubscriptionState> ENTITLING = Arrays.stream(SubscriptionState.values())
		.filter(SubscriptionState::entitles)
		.toList();

	private final SubscriptionRepository subscriptions;

	private final GooglePlayClient googlePlay;

	SubscriptionService(SubscriptionRepository subscriptions, GooglePlayClient googlePlay) {
		this.subscriptions = subscriptions;
		this.googlePlay = googlePlay;
	}

	/** Whether any of these users has a subscription that entitles them now. */
	public boolean anyEntitled(Collection<UUID> userIds) {
		return !userIds.isEmpty() && this.subscriptions.anyEntitling(userIds, ENTITLING, Instant.now());
	}

	/** The user's entitling subscription that lasts longest, else their latest one. */
	public Optional<SubscriptionView> current(UUID userId) {
		Instant now = Instant.now();
		Comparator<SubscriptionEntity> lastsLonger = Comparator.comparing(SubscriptionEntity::getExpiresAt,
				Comparator.nullsLast(Comparator.naturalOrder()));
		List<SubscriptionEntity> own = this.subscriptions.findByUserId(userId);
		return own.stream()
			.filter((subscription) -> subscription.entitles(now))
			.max(lastsLonger)
			.or(() -> own.stream()
				.max(Comparator.comparing(SubscriptionEntity::getUpdatedAt,
						Comparator.nullsFirst(Comparator.naturalOrder()))))
			.map((subscription) -> subscription.toView(now));
	}

	/**
	 * Records a Play purchase the app just made. The app tags each purchase with
	 * {@link #accountIdFor} the buyer, so a token can't be claimed by anyone else.
	 */
	@Transactional
	public SubscriptionView registerGooglePlay(UUID userId, String purchaseToken) {
		GooglePlayClient.Purchase purchase = this.googlePlay.fetch(purchaseToken);
		if (!accountIdFor(userId).equals(purchase.obfuscatedAccountId())) {
			throw invalidPurchase();
		}
		SubscriptionEntity subscription = this.subscriptions.findByPurchaseToken(purchaseToken)
			.orElseGet(() -> new SubscriptionEntity(userId, purchaseToken));
		if (!subscription.getUserId().equals(userId)) {
			throw invalidPurchase();
		}
		return store(subscription, purchaseToken, purchase).toView(Instant.now());
	}

	/**
	 * Re-reads a known purchase from Play, after one of Google's notifications or when it
	 * nears its expiry. Tokens the app hasn't reported yet are skipped: the app re-sends
	 * them itself.
	 */
	@Transactional
	public void refreshGooglePlay(String purchaseToken) {
		this.subscriptions.findByPurchaseToken(purchaseToken)
			.ifPresent((subscription) -> store(subscription, purchaseToken, this.googlePlay.fetch(purchaseToken)));
	}

	/** Play purchases to re-read: past their expiry, or reaching it within {@code within}. */
	public List<String> expiringPurchaseTokens(Duration within) {
		return this.subscriptions
			.findBySourceAndStateNotAndExpiresAtBefore(SubscriptionSource.GOOGLE_PLAY, SubscriptionState.EXPIRED,
					Instant.now().plus(within))
			.stream()
			.map(SubscriptionEntity::getPurchaseToken)
			.toList();
	}

	/**
	 * What the app passes as the obfuscated account id when buying: the SHA-256 of the
	 * user id, lower-case hex, so Play never sees the id itself.
	 */
	public static String accountIdFor(UUID userId) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(userId.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Play refunds purchases left unacknowledged for 3 days; a pending one can't be yet. */
	private SubscriptionEntity store(SubscriptionEntity subscription, String purchaseToken,
			GooglePlayClient.Purchase purchase) {
		subscription.apply(purchase);
		String replaced = purchase.linkedPurchaseToken();
		if (replaced != null) {
			this.subscriptions.findByPurchaseToken(replaced).ifPresent(SubscriptionEntity::supersede);
		}
		SubscriptionEntity saved = this.subscriptions.save(subscription);
		if (!purchase.acknowledged() && purchase.state() != SubscriptionState.PENDING) {
			this.googlePlay.acknowledge(purchase.productId(), purchaseToken);
		}
		return saved;
	}

	private static ApiException invalidPurchase() {
		return new ApiException(HttpStatus.BAD_REQUEST, "invalid-purchase", "This purchase isn't yours to register");
	}

}
