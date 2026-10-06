package com.familybudget.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

/** Immutable once recorded: correcting a transaction means deleting and re-creating it. */
@Entity
@Table(name = "transactions")
class TransactionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private TransactionKind kind;

	/** Always positive; the direction comes from the category type or the transfer. */
	@Column(nullable = false, updatable = false)
	private BigDecimal amount;

	@Column(nullable = false, updatable = false)
	private LocalDate occurredOn;

	@Column(nullable = false, updatable = false)
	private UUID recordedBy;

	@Column(updatable = false)
	private @Nullable UUID accountId;

	@Column(updatable = false)
	private @Nullable UUID categoryId;

	@Column(updatable = false)
	private @Nullable UUID fromAccountId;

	@Column(updatable = false)
	private @Nullable UUID toAccountId;

	@Column(updatable = false)
	private @Nullable String note;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	protected TransactionEntity() {
	}

	private TransactionEntity(TransactionKind kind, BigDecimal amount, LocalDate occurredOn, UUID recordedBy,
			@Nullable String note) {
		this.kind = kind;
		this.amount = amount.setScale(2);
		this.occurredOn = occurredOn;
		this.recordedBy = recordedBy;
		this.note = note;
	}

	static TransactionEntity payment(BigDecimal amount, LocalDate occurredOn, UUID recordedBy, @Nullable String note,
			UUID accountId, UUID categoryId) {
		TransactionEntity transaction = new TransactionEntity(TransactionKind.PAYMENT, amount, occurredOn, recordedBy,
				note);
		transaction.accountId = accountId;
		transaction.categoryId = categoryId;
		return transaction;
	}

	static TransactionEntity transfer(BigDecimal amount, LocalDate occurredOn, UUID recordedBy, @Nullable String note,
			UUID fromAccountId, UUID toAccountId) {
		TransactionEntity transaction = new TransactionEntity(TransactionKind.TRANSFER, amount, occurredOn, recordedBy,
				note);
		transaction.fromAccountId = fromAccountId;
		transaction.toAccountId = toAccountId;
		return transaction;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Transaction has not been persisted yet");
		}
		return this.id;
	}

	TransactionKind getKind() {
		return this.kind;
	}

	BigDecimal getAmount() {
		return this.amount;
	}

	LocalDate getOccurredOn() {
		return this.occurredOn;
	}

	UUID getRecordedBy() {
		return this.recordedBy;
	}

	@Nullable UUID getAccountId() {
		return this.accountId;
	}

	@Nullable UUID getCategoryId() {
		return this.categoryId;
	}

	@Nullable UUID getFromAccountId() {
		return this.fromAccountId;
	}

	@Nullable UUID getToAccountId() {
		return this.toAccountId;
	}

	@Nullable String getNote() {
		return this.note;
	}

	/** The accounts this transaction touches: one for a payment, two for a transfer. */
	List<UUID> accountIds() {
		return (this.kind == TransactionKind.PAYMENT) ? List.of(require(this.accountId))
				: List.of(require(this.fromAccountId), require(this.toAccountId));
	}

	static UUID require(@Nullable UUID id) {
		if (id == null) {
			throw new IllegalStateException("Transaction is missing an account or category");
		}
		return id;
	}

}
