package com.familybudget.security.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.familybudget.common.exception.ApiException;
import com.familybudget.security.service.RefreshTokenService;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

/**
 * Ends the Google login (ARCHITECTURE.md §6.1): find-or-create the user, set the same
 * refresh cookie the password path sets, and redirect to the SPA, which then calls
 * {@code /auth/refresh}. Google's tokens never leave the backend.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserService userService;

	private final RefreshTokenService refreshTokens;

	private final RefreshTokenCookies cookies;

	private final String successUrl;

	private final String failureUrl;

	OAuth2LoginSuccessHandler(UserService userService, RefreshTokenService refreshTokens, RefreshTokenCookies cookies,
			@Value("${app.frontend-url}") String frontendUrl) {
		this.userService = userService;
		this.refreshTokens = refreshTokens;
		this.cookies = cookies;
		this.successUrl = frontendUrl + "/auth/callback";
		this.failureUrl = frontendUrl + "/login?error=oauth";
	}

	public String getFailureUrl() {
		return this.failureUrl;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
			response.sendRedirect(this.failureUrl);
			return;
		}
		UserView user;
		try {
			user = this.userService.findOrCreateFromGoogle(oidcUser.getSubject(), oidcUser.getEmail(),
					Boolean.TRUE.equals(oidcUser.getEmailVerified()), oidcUser.getFullName(),
					ClientRegion.of(request));
		}
		catch (ApiException ex) {
			response.sendRedirect(this.failureUrl + "&reason=" + ex.getType());
			return;
		}
		String refreshToken = this.refreshTokens.issue(user.id());
		response.addHeader(HttpHeaders.SET_COOKIE, this.cookies.create(refreshToken, this.refreshTokens.getTtl()).toString());
		response.sendRedirect(this.successUrl);
	}

}
