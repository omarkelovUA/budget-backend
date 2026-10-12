package com.familybudget.account.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.account.model.AccountRequest;
import com.familybudget.user.event.UserRegistered;
import com.familybudget.user.service.UserService;

/**
 * A new user starts with a couple of common accounts, named in their language and in their
 * default currency. They're ordinary accounts: the user changes or deletes them freely.
 */
@Component
public class DefaultAccountSetup {

	private final AccountService accountService;

	private final UserService userService;

	DefaultAccountSetup(AccountService accountService, UserService userService) {
		this.accountService = accountService;
		this.userService = userService;
	}

	@EventListener
	@Transactional
	void on(UserRegistered event) {
		String currency = this.userService.settings(event.userId()).defaultCurrency();
		boolean ukrainian = "uk".equals(event.language());
		for (AccountRequest account : List.of(
				request(ukrainian ? "Готівка" : "Cash", currency, "cash", "green"),
				request(ukrainian ? "Картка" : "Card", currency, "card", "blue"))) {
			this.accountService.create(event.userId(), account);
		}
	}

	private static AccountRequest request(String name, String currency, String icon, String color) {
		return new AccountRequest(null, name, BigDecimal.ZERO, currency, "assets/" + icon + ".svg", color, null);
	}

}
