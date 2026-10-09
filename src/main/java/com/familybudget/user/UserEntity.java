package com.familybudget.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

	/** ISO 4217; picked from the region at sign-up, then changed in settings. */
	@Column(nullable = false)
	private String defaultCurrency;

	/** ISO 4217 codes pickers offer, in the user's order; always holds the default. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> usedCurrencies = new ArrayList<>();

	/** ISO 639-1, one of {@link SupportedLanguage}; picked from the region at sign-up. */
	@Column(nullable = false)
	private String language;

	/** A {@link Theme} code; new users follow their device. */
	@Column(nullable = false)
	private String theme = Theme.SYSTEM.code();

	/** The OKLCH hue (0–359) of the UI's primary colour; new users get the "dollar" green. */
	@Column(nullable = false)
	private short primaryHue = 150;

	/** See {@link UserService#sessionVersion}. */
	@Column(nullable = false)
	private int sessionVersion;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	protected UserEntity() {
	}

	UserEntity(String email, String displayName, SignUpRegion region) {
		this.email = email;
		this.displayName = displayName;
		this.language = region.language();
		this.defaultCurrency = region.defaultCurrency();
		this.usedCurrencies = new ArrayList<>(List.of(region.defaultCurrency()));
	}

	UserSettingsView settings() {
		return new UserSettingsView(this.language, this.defaultCurrency, List.copyOf(this.usedCurrencies), this.theme,
				this.primaryHue);
	}

	void changeSettings(String language, String defaultCurrency, List<String> usedCurrencies, Theme theme,
			int primaryHue) {
		this.language = language;
		this.theme = theme.code();
		this.primaryHue = (short) primaryHue;
		this.defaultCurrency = defaultCurrency;
		this.usedCurrencies = new ArrayList<>(usedCurrencies);
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
