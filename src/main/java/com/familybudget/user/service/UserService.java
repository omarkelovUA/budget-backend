package com.familybudget.user.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.common.model.SyncWindow;
import com.familybudget.user.entity.UserEntity;
import com.familybudget.user.event.UserRegistered;
import com.familybudget.user.event.UserSessionsInvalidated;
import com.familybudget.user.model.CurrentUserView;
import com.familybudget.user.model.DailyCount;
import com.familybudget.user.model.SignUpRegion;
import com.familybudget.user.model.SupportedLanguage;
import com.familybudget.user.model.Theme;
import com.familybudget.user.model.UserSettingsView;
import com.familybudget.user.model.UserStatsView;
import com.familybudget.user.model.UserView;
import com.familybudget.user.repository.UserRepository;

/** Public API of the user module. */
@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository users;

	private final ApplicationEventPublisher events;

	/** Who runs the service (ARCHITECTURE.md §3.5), by normalized email. */
	private final Set<String> operatorEmails;

	public static final int MAX_USED_CURRENCIES = 20;

	/** How far back the operator page's daily numbers go. */
	static final int STATS_DAYS = 30;

	UserService(UserRepository users, ApplicationEventPublisher events,
			@Value("${app.operator.emails}") List<String> operatorEmails) {
		this.users = users;
		this.events = events;
		this.operatorEmails = operatorEmails.stream()
			.filter((email) -> !email.isBlank())
			.map(UserService::normalizeEmail)
			.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Finds the user for a Google login, the only way in, or creates them. An account from
	 * before that (made with a password, its email never verified) and with the same
	 * Google-verified email gets linked, which ends its existing sessions: whoever made it
	 * may not be the person Google just vouched for.
	 */
	@Transactional
	public UserView findOrCreateFromGoogle(String subject, @Nullable String email, boolean emailVerified,
			@Nullable String name, SignUpRegion region) {
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

	/** The caller for {@code GET /users/me}. */
	public CurrentUserView current(UUID id) {
		UserEntity user = entity(id);
		return new CurrentUserView(id, user.getEmail(), user.getDisplayName(), isOperator(user));
	}

	/**
	 * Only for a Google-linked account: its email is the one Google verified, so nobody can
	 * become an operator by claiming the address.
	 */
	public void requireOperator(UUID id) {
		if (!isOperator(entity(id))) {
			throw new ApiException(HttpStatus.FORBIDDEN, "operator-required", "Only the service's operators can do this");
		}
	}

	private boolean isOperator(UserEntity user) {
		return user.getGoogleSubject() != null && this.operatorEmails.contains(user.getEmail());
	}

	/** A session of the user started or refreshed today (UTC); once per day is enough. */
	@Transactional
	public void recordActivity(UUID id) {
		this.users.recordActivity(id, LocalDate.now(ZoneOffset.UTC));
	}

	public UserStatsView stats() {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		LocalDate from = today.minusDays(STATS_DAYS - 1);
		Instant now = Instant.now();
		return new UserStatsView(this.users.count(), this.users.countByCreatedAtAfter(now.minus(Duration.ofDays(7))),
				this.users.countByCreatedAtAfter(now.minus(Duration.ofDays(STATS_DAYS))),
				this.users.countActiveSince(from),
				daily(from, today, this.users.signupsPerDaySince(from.atStartOfDay(ZoneOffset.UTC).toInstant())),
				daily(from, today, this.users.activePerDaySince(from)));
	}

	/** Every day from {@code from} to {@code to}, those without a row counted 0. */
	private static List<DailyCount> daily(LocalDate from, LocalDate to, List<UserRepository.DayCount> rows) {
		Map<LocalDate, Long> counts = rows.stream()
			.collect(Collectors.toMap(UserRepository.DayCount::getDay, UserRepository.DayCount::getCount));
		return from.datesUntil(to.plusDays(1)).map((day) -> new DailyCount(day, counts.getOrDefault(day, 0L))).toList();
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
	 * Whether the user's email is known to be theirs: only an account linked to Google
	 * (which checked the email) counts, not one left from the password days.
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
