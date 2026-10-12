package com.familybudget.account.entity;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;

import com.familybudget.account.model.AccountAccess;
import com.familybudget.account.model.AccountRef;
import com.familybudget.account.model.AccountRequest;
import com.familybudget.account.model.GrantLevel;
import com.familybudget.account.model.TransientAccountView;
import com.familybudget.common.entity.ClientIdEntity;

/**
 * {@code @DynamicUpdate}: balances also move through {@link AccountRepository#addToBalance};
 * writing only changed columns keeps a loaded copy from overwriting that with a stale value.
 */
@Entity
@Table(name = "accounts")
@DynamicUpdate
public class AccountEntity extends ClientIdEntity {

	@Column(nullable = false, updatable = false)
	private UUID ownerUserId;

	/** Set on transient accounts only: who the owner (the sender) is sending money to. */
	@Column(updatable = false)
	private @Nullable UUID transientRecipientId;

	/** Family members the owner gave access to, by user id. */
	@ElementCollection
	@CollectionTable(name = "account_access", joinColumns = @JoinColumn(name = "account_id"))
	@MapKeyColumn(name = "user_id")
	@Column(name = "level", nullable = false)
	@Enumerated(EnumType.STRING)
	@BatchSize(size = 100)
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

	public AccountEntity(UUID ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	public UUID getOwnerUserId() {
		return this.ownerUserId;
	}

	public Map<UUID, GrantLevel> getGrants() {
		return this.grants;
	}

	public BigDecimal getBalance() {
		return this.balance;
	}

	public String getCurrency() {
		return this.currency;
	}

	public boolean isClosed() {
		return this.closedAt != null;
	}

	@Nullable UUID getTransientRecipientId() {
		return this.transientRecipientId;
	}

	public boolean isTransient() {
		return this.transientRecipientId != null;
	}

	public TransientAccountView toTransientView() {
		if (this.transientRecipientId == null) {
			throw new IllegalStateException("Not a transient account");
		}
		return new TransientAccountView(getId(), this.ownerUserId, this.transientRecipientId, this.currency,
				this.balance);
	}

	public AccountAccess accessFor(UUID userId) {
		if (this.ownerUserId.equals(userId)) {
			return AccountAccess.OWNER;
		}
		GrantLevel grant = this.grants.get(userId);
		if (grant == null) {
			return AccountAccess.NONE;
		}
		return (grant == GrantLevel.SHARED) ? AccountAccess.SHARED : AccountAccess.VISIBLE;
	}

	public void apply(AccountRequest request, Map<UUID, GrantLevel> grants) {
		this.name = request.name().strip();
		this.balance = request.balance().setScale(2);
		this.currency = request.currency();
		this.icon = request.icon();
		this.color = request.color();
		this.grants.clear();
		this.grants.putAll(grants);
	}

	public AccountRef toRef(UUID viewerId) {
		return new AccountRef(getId(), this.name, this.currency, this.icon, this.color, accessFor(viewerId),
				isClosed(), isTransient());
	}

	public String getName() {
		return this.name;
	}

	public String getIcon() {
		return this.icon;
	}

	public String getColor() {
		return this.color;
	}

}
