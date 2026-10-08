package com.familybudget.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.ApiException;
import com.familybudget.user.UserService;
import com.familybudget.user.UserView;

/**
 * Password login path of ARCHITECTURE.md §6.1. Access token in the body, refresh token
 * in an httpOnly cookie; the Google path converges on {@link #refresh}.
 */
@RestController
@RequestMapping(RefreshTokenCookies.PATH)
class AuthController {

	private final UserService userService;

	private final AccessTokenService accessTokens;

	private final RefreshTokenService refreshTokens;

	private final RefreshTokenCookies cookies;

	AuthController(UserService userService, AccessTokenService accessTokens, RefreshTokenService refreshTokens,
			RefreshTokenCookies cookies) {
		this.userService = userService;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.cookies = cookies;
	}

	@PostMapping("/register")
	ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
		UserView user = this.userService.register(request.email(), request.displayName(), request.password(),
				ClientRegion.of(httpRequest));
		return startSession(HttpStatus.CREATED, user);
	}

	@PostMapping("/login")
	ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		UserView user = this.userService.authenticate(request.email(), request.password())
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid-credentials",
					"Email or password is incorrect"));
		return startSession(HttpStatus.OK, user);
	}

	@PostMapping("/refresh")
	ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshTokenCookies.NAME, required = false) @Nullable String token) {
		if (token == null || token.isEmpty()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid-refresh-token", "No refresh token");
		}
		RefreshTokenService.Rotation rotation = this.refreshTokens.rotate(token);
		UserView user = this.userService.get(rotation.userId());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, this.cookies.create(rotation.token(), this.refreshTokens.getTtl()).toString())
			.body(this.accessTokens.issue(user));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@CookieValue(name = RefreshTokenCookies.NAME, required = false) @Nullable String token) {
		if (token != null && !token.isEmpty()) {
			this.refreshTokens.revoke(token);
		}
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, this.cookies.clear().toString()).build();
	}

	private ResponseEntity<AuthResponse> startSession(HttpStatus status, UserView user) {
		String refreshToken = this.refreshTokens.issue(user.id());
		return ResponseEntity.status(status)
			.header(HttpHeaders.SET_COOKIE, this.cookies.create(refreshToken, this.refreshTokens.getTtl()).toString())
			.body(this.accessTokens.issue(user));
	}

	record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 100) String displayName, @NotBlank @Size(min = 8, max = 128) String password) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

}
