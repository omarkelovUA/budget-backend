package com.familybudget.account;

import java.math.BigDecimal;
import java.util.UUID;

import com.familybudget.common.Visibility;

public record AccountView(UUID id, String name, BigDecimal balance, String currency, String icon, String color,
		Visibility visibility) {

}
