package com.familybudget.security;

import com.familybudget.user.UserView;

record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {

}
