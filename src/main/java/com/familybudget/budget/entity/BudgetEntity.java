package com.familybudget.budget.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import com.familybudget.common.entity.ClientIdEntity;
import com.familybudget.common.model.OwnerType;

@Entity
@Table(name = "budgets")
public class BudgetEntity extends ClientIdEntity {

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

	public BudgetEntity(OwnerType ownerType, UUID ownerId) {
		this.ownerType = ownerType;
		this.ownerId = ownerId;
	}

	public OwnerType getOwnerType() {
		return this.ownerType;
	}

	public UUID getOwnerId() {
		return this.ownerId;
	}

	public UUID getCategoryId() {
		return this.categoryId;
	}

	public YearMonth getPeriod() {
		return YearMonth.parse(this.period);
	}

	public BigDecimal getPlannedAmount() {
		return this.plannedAmount;
	}

	public void apply(UUID categoryId, YearMonth period, BigDecimal plannedAmount) {
		this.categoryId = categoryId;
		this.period = period.toString();
		this.plannedAmount = plannedAmount.setScale(2);
	}

}
