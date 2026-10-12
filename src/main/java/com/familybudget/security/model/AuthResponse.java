package com.familybudget.security.model;

import com.familybudget.user.model.UserView;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {

}
