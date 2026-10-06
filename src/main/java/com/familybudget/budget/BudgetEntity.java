package com.familybudget.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
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

@Entity
@Table(name = "budgets")
class BudgetEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private OwnerType ownerType;

	@Column(nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false)
	private UUID categoryId;

	/** {@code YYYY-MM}. */
	@Column(nullable = false)
	private String period;

	@Column(nullable = false)
	private BigDecimal plannedAmount;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	protected BudgetEntity() {
	}

	BudgetEntity(OwnerType ownerType, UUID ownerId) {
		this.ownerType = ownerType;
		this.ownerId = ownerId;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Budget has not been persisted yet");
		}
		return this.id;
	}

	OwnerType getOwnerType() {
		return this.ownerType;
	}

	UUID getOwnerId() {
		return this.ownerId;
	}

	UUID getCategoryId() {
		return this.categoryId;
	}

	YearMonth getPeriod() {
		return YearMonth.parse(this.period);
	}

	BigDecimal getPlannedAmount() {
		return this.plannedAmount;
	}

	void apply(UUID categoryId, YearMonth period, BigDecimal plannedAmount) {
		this.categoryId = categoryId;
		this.period = period.toString();
		this.plannedAmount = plannedAmount.setScale(2);
	}

}
