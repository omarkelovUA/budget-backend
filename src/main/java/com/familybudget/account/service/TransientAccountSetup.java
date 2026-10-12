package com.familybudget.account.service;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.family.event.FamilyMembershipStarted;
import com.familybudget.family.service.FamilyService;
import com.familybudget.user.service.UserService;

/**
 * Someone joining a family gets transient accounts with every other member, both ways,
 * each in the sender's default currency (PLAN.md M14). Other currencies appear on demand.
 */
@Component
public class TransientAccountSetup {

	private final AccountService accountService;

	private final FamilyService familyService;

	private final UserService userService;

	TransientAccountSetup(AccountService accountService, FamilyService familyService, UserService userService) {
		this.accountService = accountService;
		this.familyService = familyService;
		this.userService = userService;
	}

	@EventListener
	@Transactional
	void on(FamilyMembershipStarted event) {
		UUID newcomer = event.userId();
		String newcomersCurrency = this.userService.settings(newcomer).defaultCurrency();
		for (UUID member : this.familyService.memberIds(event.familyId())) {
			if (member.equals(newcomer)) {
				continue;
			}
			this.accountService.openTransient(newcomer, member, newcomersCurrency);
			this.accountService.openTransient(member, newcomer, this.userService.settings(member).defaultCurrency());
		}
	}

}
