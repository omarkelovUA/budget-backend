package com.familybudget.security.model;

import com.familybudget.user.model.UserView;

/** {@link AuthResponse} plus the refresh token, which the mobile app keeps itself. */
public record MobileAuthResponse(String accessToken, String tokenType, long expiresIn, UserView user,
		String refreshToken) {

	public static MobileAuthResponse of(AuthResponse auth, String refreshToken) {
		return new MobileAuthResponse(auth.accessToken(), auth.tokenType(), auth.expiresIn(), auth.user(),
				refreshToken);
	}

}
