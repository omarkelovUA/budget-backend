package com.familybudget.subscription.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.familybudget.subscription.model.SubscriptionSource;
import com.familybudget.subscription.model.SubscriptionState;
import com.familybudget.subscription.model.SubscriptionView;
import com.familybudget.subscription.service.GooglePlayClient;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private SubscriptionSource source;

	@Column(updatable = false)
	private @Nullable String purchaseToken;

	private @Nullable String productId;

	private @Nullable String basePlanId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SubscriptionState state;

	private @Nullable Instant expiresAt;

	@Column(nullable = false)
	private boolean autoRenewing;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private @Nullable Instant updatedAt;

	protected SubscriptionEntity() {
	}

	/** A Play purchase, filled in by {@link #apply}. Manual grants are inserted by hand. */
	public SubscriptionEntity(UUID userId, String purchaseToken) {
		this.userId = userId;
		this.source = SubscriptionSource.GOOGLE_PLAY;
		this.purchaseToken = purchaseToken;
		this.state = SubscriptionState.PENDING;
	}

	public void apply(GooglePlayClient.Purchase purchase) {
		this.state = purchase.state();
		this.productId = purchase.productId();
		this.basePlanId = purchase.basePlanId();
		this.expiresAt = purchase.expiresAt();
		this.autoRenewing = purchase.autoRenewing();
	}

	/** Replaced by a newer purchase (an upgrade, a resubscribe); only the new one counts. */
	public void supersede() {
		this.state = SubscriptionState.EXPIRED;
		this.autoRenewing = false;
	}

	public boolean entitles(Instant now) {
		return this.state.entitles() && (this.expiresAt == null || this.expiresAt.isAfter(now));
	}

	public UUID getUserId() {
		return this.userId;
	}

	public @Nullable String getPurchaseToken() {
		return this.purchaseToken;
	}

	public @Nullable Instant getExpiresAt() {
		return this.expiresAt;
	}

	public @Nullable Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public SubscriptionView toView(Instant now) {
		return new SubscriptionView(this.source, this.state, this.productId, this.basePlanId, this.expiresAt,
				this.autoRenewing, entitles(now));
	}

}
