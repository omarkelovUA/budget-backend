package com.familybudget.user.service;

import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.common.model.SyncWindow;
import com.familybudget.user.entity.UserEntity;
import com.familybudget.user.event.UserRegistered;
import com.familybudget.user.event.UserSessionsInvalidated;
import com.familybudget.user.model.SignUpRegion;
import com.familybudget.user.model.SupportedLanguage;
import com.familybudget.user.model.Theme;
import com.familybudget.user.model.UserSettingsView;
import com.familybudget.user.model.UserView;
import com.familybudget.user.repository.UserRepository;

/** Public API of the user module. */
@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	public static final int MAX_USED_CURRENCIES = 20;

	/** Compared against when the email is unknown, so login timing doesn't reveal it. */
	private final String dummyPasswordHash;

	UserService(UserRepository users, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public UserView register(String email, String displayName, String rawPassword, SignUpRegion region) {
		String normalizedEmail = normalizeEmail(email);
		if (this.users.existsByEmail(normalizedEmail)) {
			throw new ApiException(HttpStatus.CONFLICT, "email-taken", "An account with this email already exists");
		}
		UserEntity user = new UserEntity(normalizedEmail, displayName.strip(), region);
		user.setPasswordHash(this.passwordEncoder.encode(rawPassword));
		return registered(this.users.save(user));
	}

	public Optional<UserView> authenticate(String email, String rawPassword) {
		Optional<UserEntity> user = this.users.findByEmail(normalizeEmail(email));
		String hash = user.map(UserEntity::getPasswordHash).orElse(null);
		boolean matches = this.passwordEncoder.matches(rawPassword, (hash != null) ? hash : this.dummyPasswordHash);
		return (matches && hash != null) ? user.map(UserEntity::toView) : Optional.empty();
	}

	/**
	 * Finds the user for a Google login, linking an existing account with the same
	 * (Google-verified) email or creating a new one. Password sign-up never verified that
	 * email, so linking also removes the password and ends every existing session: whoever
	 * registered it may not be the person Google just vouched for. Without
	 * {@code confirmLink} such a link is refused, so the client can warn the user first.
	 */
	@Transactional
	public UserView findOrCreateFromGoogle(String subject, @Nullable String email, boolean emailVerified,
			@Nullable String name, SignUpRegion region, boolean confirmLink) {
		Optional<UserEntity> linked = this.users.findByGoogleSubject(subject);
		if (linked.isPresent()) {
			return linked.get().toView();
		}
		if (email == null || !emailVerified) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "email-not-verified",
					"Google account has no verified email");
		}
		String normalizedEmail = normalizeEmail(email);
		Optional<UserEntity> existing = this.users.findByEmail(normalizedEmail);
		if (existing.isPresent() && !confirmLink) {
			throw new ApiException(HttpStatus.CONFLICT, "google-link-confirmation-required",
					"An account with this email exists; confirm linking it to Google");
		}
		UserEntity user = existing
			.orElseGet(() -> new UserEntity(normalizedEmail, displayNameOrDefault(name, normalizedEmail), region));
		user.linkGoogle(subject);
		if (existing.isEmpty()) {
			return registered(this.users.save(user));
		}
		user.reclaimForVerifiedOwner();
		this.events.publishEvent(new UserSessionsInvalidated(user.getId()));
		return this.users.save(user).toView();
	}

	private UserView registered(UserEntity user) {
		this.events.publishEvent(new UserRegistered(user.getId(), user.settings().language()));
		return user.toView();
	}

	public UserView get(UUID id) {
		return this.users.findById(id).map(UserEntity::toView).orElseThrow(() -> new NotFoundException("User not found"));
	}

	public List<UserView> getAll(Collection<UUID> ids) {
		return this.users.findAllById(ids).stream().map(UserEntity::toView).toList();
	}

	/** Sync feed (ARCHITECTURE.md §6.5): whether any of these users was written in {@code window}. */
	public boolean anyChangedIn(Collection<UUID> ids, SyncWindow window) {
		return !ids.isEmpty() && this.users.anyChanged(ids, window.since(), window.until());
	}

	public UserSettingsView settings(UUID id) {
		return entity(id).settings();
	}

	/** Replaces all settings; the used currencies keep their order, duplicates dropped. */
	@Transactional
	public UserSettingsView updateSettings(UUID id, UserSettingsView settings) {
		String language = SupportedLanguage.fromCode(settings.language())
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "unsupported-language",
					"Unsupported language: " + settings.language()))
			.code();
		String defaultCurrency = requireCurrency(settings.defaultCurrency());
		List<String> usedCurrencies = settings.usedCurrencies()
			.stream()
			.map(UserService::requireCurrency)
			.distinct()
			.toList();
		if (usedCurrencies.isEmpty() || usedCurrencies.size() > MAX_USED_CURRENCIES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-used-currencies",
					"Between 1 and " + MAX_USED_CURRENCIES + " used currencies are allowed");
		}
		if (!usedCurrencies.contains(defaultCurrency)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "default-currency-not-used",
					"The default currency must be one of the used currencies");
		}
		Theme theme = Theme.fromCode(settings.theme())
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "unsupported-theme",
					"Unsupported theme: " + settings.theme()));
		UserEntity user = entity(id);
		user.changeSettings(language, defaultCurrency, usedCurrencies, theme, settings.primaryHue());
		return user.settings();
	}

	private UserEntity entity(UUID id) {
		return this.users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
	}

	private static String requireCurrency(String code) {
		try {
			return Currency.getInstance(code).getCurrencyCode();
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-currency", "Unknown currency code: " + code);
		}
	}

	public Optional<UserView> findByEmail(String email) {
		return this.users.findByEmail(normalizeEmail(email)).map(UserEntity::toView);
	}

	/**
	 * Whether the user's email is known to be theirs. Password registration doesn't verify
	 * it, so only an account linked to Google (which checked the email) counts.
	 */
	public boolean hasVerifiedEmail(UUID id) {
		return this.users.findById(id).map((user) -> user.getGoogleSubject() != null).orElse(false);
	}

	/**
	 * Access tokens carry this as their {@code sv} claim and stop being accepted once it
	 * changes, so bumping it ends every session within the request, not after the TTL.
	 */
	public int sessionVersion(UUID id) {
		return this.users.findById(id).map(UserEntity::getSessionVersion).orElse(-1);
	}

	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static String displayNameOrDefault(@Nullable String name, String email) {
		return (name != null && !name.isBlank()) ? name.strip() : email.substring(0, email.indexOf('@'));
	}

}
