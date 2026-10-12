package com.familybudget.security.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.security.model.GoogleIdentity;
import com.familybudget.security.model.MobileAuthResponse;
import com.familybudget.security.service.AccessTokenService;
import com.familybudget.security.service.GoogleIdTokenVerifier;
import com.familybudget.security.service.RefreshTokenService;
import com.familybudget.security.web.ClientRegion;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

/**
 * Mobile login path of ARCHITECTURE.md §6.1: Google only. The app sends the ID token it
 * got from Credential Manager and keeps both of our tokens itself, so they travel in
 * the body instead of a cookie.
 */
@RestController
class MobileAuthController {

	private final UserService userService;

	private final GoogleIdTokenVerifier googleIdTokens;

	private final AccessTokenService accessTokens;

	private final RefreshTokenService refreshTokens;

	MobileAuthController(UserService userService, GoogleIdTokenVerifier googleIdTokens,
			AccessTokenService accessTokens, RefreshTokenService refreshTokens) {
		this.userService = userService;
		this.googleIdTokens = googleIdTokens;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
	}

	@PostMapping("/api/v1/auth/google/mobile")
	MobileAuthResponse signIn(@Valid @RequestBody GoogleMobileRequest request, HttpServletRequest httpRequest) {
		GoogleIdentity google = this.googleIdTokens.verify(request.idToken());
		UserView user = this.userService.findOrCreateFromGoogle(google.subject(), google.email(),
				google.emailVerified(), google.name(), ClientRegion.of(httpRequest), request.confirmLink());
		return MobileAuthResponse.of(this.accessTokens.issue(user), this.refreshTokens.issue(user.id()));
	}

	@PostMapping("/api/v1/auth/mobile/refresh")
	MobileAuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
		RefreshTokenService.Rotation rotation = this.refreshTokens.rotate(request.refreshToken());
		return MobileAuthResponse.of(this.accessTokens.issue(this.userService.get(rotation.userId())), rotation.token());
	}

	@PostMapping("/api/v1/auth/mobile/logout")
	ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
		this.refreshTokens.revoke(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	record GoogleMobileRequest(@NotBlank String idToken, boolean confirmLink) {
	}

	record RefreshRequest(@NotBlank String refreshToken) {
	}

}
