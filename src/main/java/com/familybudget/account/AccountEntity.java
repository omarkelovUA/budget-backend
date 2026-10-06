package com.familybudget.account;

import java.math.BigDecimal;
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
import org.jspecify.annotations.Nullable;

import com.familybudget.common.OwnerType;
import com.familybudget.common.Visibility;

@Entity
@Table(name = "accounts")
class AccountEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private OwnerType ownerType;

	@Column(nullable = false, updatable = false)
	private UUID ownerId;

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

	protected AccountEntity() {
	}

	AccountEntity(OwnerType ownerType, UUID ownerId) {
		this.ownerType = ownerType;
		this.ownerId = ownerId;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Account has not been persisted yet");
		}
		return this.id;
	}

	OwnerType getOwnerType() {
		return this.ownerType;
	}

	UUID getOwnerId() {
		return this.ownerId;
	}

	void apply(AccountRequest request) {
		this.name = request.name().strip();
		this.balance = request.balance().setScale(2);
		this.currency = request.currency();
		this.icon = request.icon();
		this.color = request.color();
	}

	AccountView toView() {
		return new AccountView(getId(), this.name, this.balance, this.currency, this.icon, this.color,
				Visibility.of(this.ownerType));
	}

	AccountRef toRef() {
		return new AccountRef(getId(), this.name, this.currency, this.icon, this.color, Visibility.of(this.ownerType));
	}

}
