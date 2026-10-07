package com.familybudget.user;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.ApiException;
import com.familybudget.common.NotFoundException;

/** Public API of the user module. */
@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	/** Compared against when the email is unknown, so login timing doesn't reveal it. */
	private final String dummyPasswordHash;

	UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public UserView register(String email, String displayName, String rawPassword) {
		String normalizedEmail = normalizeEmail(email);
		if (this.users.existsByEmail(normalizedEmail)) {
			throw new ApiException(HttpStatus.CONFLICT, "email-taken", "An account with this email already exists");
		}
		UserEntity user = new UserEntity(normalizedEmail, displayName.strip());
		user.setPasswordHash(this.passwordEncoder.encode(rawPassword));
		return this.users.save(user).toView();
	}

	public Optional<UserView> authenticate(String email, String rawPassword) {
		Optional<UserEntity> user = this.users.findByEmail(normalizeEmail(email));
		String hash = user.map(UserEntity::getPasswordHash).orElse(null);
		boolean matches = this.passwordEncoder.matches(rawPassword, (hash != null) ? hash : this.dummyPasswordHash);
		return (matches && hash != null) ? user.map(UserEntity::toView) : Optional.empty();
	}

	/**
	 * Finds the user for a Google login, linking an existing account with the same
	 * (Google-verified) email or creating a new one.
	 */
	@Transactional
	public UserView findOrCreateFromGoogle(String subject, @Nullable String email, boolean emailVerified,
			@Nullable String name) {
		Optional<UserEntity> linked = this.users.findByGoogleSubject(subject);
		if (linked.isPresent()) {
			return linked.get().toView();
		}
		if (email == null || !emailVerified) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "email-not-verified",
					"Google account has no verified email");
		}
		String normalizedEmail = normalizeEmail(email);
		UserEntity user = this.users.findByEmail(normalizedEmail)
			.orElseGet(() -> new UserEntity(normalizedEmail, displayNameOrDefault(name, normalizedEmail)));
		user.linkGoogle(subject);
		return this.users.save(user).toView();
	}

	public UserView get(UUID id) {
		return this.users.findById(id).map(UserEntity::toView).orElseThrow(() -> new NotFoundException("User not found"));
	}

	public List<UserView> getAll(Collection<UUID> ids) {
		return this.users.findAllById(ids).stream().map(UserEntity::toView).toList();
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

	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static String displayNameOrDefault(@Nullable String name, String email) {
		return (name != null && !name.isBlank()) ? name.strip() : email.substring(0, email.indexOf('@'));
	}

}
