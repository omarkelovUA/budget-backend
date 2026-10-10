package com.familybudget.category;

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
@Table(name = "categories")
class CategoryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	// A category moves between a user and their family: shared, hidden or handed over.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OwnerType ownerType;

	@Column(nullable = false)
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private CategoryType type;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String icon;

	@Column(nullable = false)
	private String color;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	/** The member (not the admin) who added this family category, until the admin has seen it. */
	private @Nullable UUID addedBy;

	protected CategoryEntity() {
	}

	CategoryEntity(OwnerType ownerType, UUID ownerId, CategoryType type) {
		this.ownerType = ownerType;
		this.ownerId = ownerId;
		this.type = type;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("Category has not been persisted yet");
		}
		return this.id;
	}

	OwnerType getOwnerType() {
		return this.ownerType;
	}

	UUID getOwnerId() {
		return this.ownerId;
	}

	CategoryType getType() {
		return this.type;
	}

	String getName() {
		return this.name;
	}

	@Nullable UUID getAddedBy() {
		return this.addedBy;
	}

	/** The same type and name, ignoring case: two such categories are duplicates. */
	boolean sameAs(CategoryType type, String name) {
		return this.type == type && this.name.equalsIgnoreCase(name.strip());
	}

	/** Makes it a family category; {@code addedBy} is set when a member other than the admin did it. */
	void shareWith(UUID familyId, @Nullable UUID addedBy) {
		this.ownerType = OwnerType.FAMILY;
		this.ownerId = familyId;
		this.addedBy = addedBy;
	}

	void hideTo(UUID userId) {
		this.ownerType = OwnerType.USER;
		this.ownerId = userId;
		this.addedBy = null;
	}

	void markSeen() {
		this.addedBy = null;
	}

	void apply(CategoryRequest request) {
		this.name = request.name().strip();
		this.icon = request.icon();
		this.color = request.color();
	}

	CategoryView toView() {
		return new CategoryView(getId(), this.type, this.name, this.icon, this.color, Visibility.of(this.ownerType),
				false);
	}

}
