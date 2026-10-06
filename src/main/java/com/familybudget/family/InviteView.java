package com.familybudget.family;

import java.time.Instant;

public record InviteView(String code, Instant expiresAt) {

}
