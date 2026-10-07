package com.familybudget.user;

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
@Table(name = "users")
class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private @Nullable UUID id;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String displayName;

	/** Null for users who only ever signed in with Google. */
	private @Nullable String passwordHash;

	private @Nullable String googleSubject;

	/** See {@link UserService#sessionVersion}. */
	@Column(nullable = false)
	private int sessionVersion;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	protected UserEntity() {
	}

	UserEntity(String email, String displayName) {
		this.email = email;
		this.displayName = displayName;
	}

	UUID getId() {
		if (this.id == null) {
			throw new IllegalStateException("User has not been persisted yet");
		}
		return this.id;
	}

	String getEmail() {
		return this.email;
	}

	String getDisplayName() {
		return this.displayName;
	}

	@Nullable String getPasswordHash() {
		return this.passwordHash;
	}

	void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	@Nullable String getGoogleSubject() {
		return this.googleSubject;
	}

	void linkGoogle(String googleSubject) {
		this.googleSubject = googleSubject;
	}

	int getSessionVersion() {
		return this.sessionVersion;
	}

	/**
	 * Google just proved who owns this email, while the password was set by whoever
	 * registered it, unverified. Dropping the password and every session locks out anyone
	 * who squatted the address before its owner showed up.
	 */
	void reclaimForVerifiedOwner() {
		this.passwordHash = null;
		this.sessionVersion++;
	}

	UserView toView() {
		return new UserView(getId(), this.email, this.displayName);
	}

}
