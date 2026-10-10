package com.familybudget.family;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "families")
class FamilyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, updatable = false)
	private UUID createdBy;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	/** When the admin chose which categories the family sees, on its first other member. */
	private @Nullable Instant categoriesReviewedAt;

	protected FamilyEntity() {
	}

	FamilyEntity(String name, UUID createdBy) {
		this.name = name;
		this.createdBy = createdBy;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Family has not been persisted yet");
		}
		return this.id;
	}

	String getName() {
		return this.name;
	}

	void rename(String name) {
		this.name = name;
	}

	boolean categoriesReviewed() {
		return this.categoriesReviewedAt != null;
	}

	void markCategoriesReviewed() {
		if (this.categoriesReviewedAt == null) {
			this.categoriesReviewedAt = Instant.now();
		}
	}

}
