package com.familybudget.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;

/**
 * {@code @DynamicUpdate}: balances also move through {@link AccountRepository#addToBalance};
 * writing only changed columns keeps a loaded copy from overwriting that with a stale value.
 */
@Entity
@Table(name = "accounts")
@DynamicUpdate
class AccountEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ownerUserId;

	/** Family members the owner gave access to, by user id. */
	@ElementCollection
	@CollectionTable(name = "account_access", joinColumns = @JoinColumn(name = "account_id"))
	@MapKeyColumn(name = "user_id")
	@Column(name = "level", nullable = false)
	@Enumerated(EnumType.STRING)
	private Map<UUID, GrantLevel> grants = new HashMap<>();

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private BigDecimal balance;

	@Column(nullable = false)
	private String currency;

	@Column(nullable = false)
	private String icon;

	@Column(nullable = false)
	private String color;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	private @Nullable Instant closedAt;

	protected AccountEntity() {
	}

	AccountEntity(UUID ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Account has not been persisted yet");
		}
		return this.id;
	}

	UUID getOwnerUserId() {
		return this.ownerUserId;
	}

	Map<UUID, GrantLevel> getGrants() {
		return this.grants;
	}

	BigDecimal getBalance() {
		return this.balance;
	}

	String getCurrency() {
		return this.currency;
	}

	boolean isClosed() {
		return this.closedAt != null;
	}

	AccountAccess accessFor(UUID userId) {
		if (this.ownerUserId.equals(userId)) {
			return AccountAccess.OWNER;
		}
		GrantLevel grant = this.grants.get(userId);
		if (grant == null) {
			return AccountAccess.NONE;
		}
		return (grant == GrantLevel.SHARED) ? AccountAccess.SHARED : AccountAccess.VISIBLE;
	}

	void apply(AccountRequest request, Map<UUID, GrantLevel> grants) {
		this.name = request.name().strip();
		this.balance = request.balance().setScale(2);
		this.currency = request.currency();
		this.icon = request.icon();
		this.color = request.color();
		this.grants.clear();
		this.grants.putAll(grants);
	}

	AccountRef toRef(UUID viewerId) {
		return new AccountRef(getId(), this.name, this.currency, this.icon, this.color, accessFor(viewerId),
				isClosed());
	}

	String getName() {
		return this.name;
	}

	String getIcon() {
		return this.icon;
	}

	String getColor() {
		return this.color;
	}

}
