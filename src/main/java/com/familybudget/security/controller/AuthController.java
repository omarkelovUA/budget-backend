package com.familybudget.security.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.exception.ApiException;
import com.familybudget.security.model.AuthResponse;
import com.familybudget.security.service.AccessTokenService;
import com.familybudget.security.service.RefreshTokenService;
import com.familybudget.security.web.RefreshTokenCookies;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

/**
 * Web session endpoints of ARCHITECTURE.md §6.1. The Google login sets the httpOnly
 * refresh cookie, then the SPA trades it here for an access token.
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

}
