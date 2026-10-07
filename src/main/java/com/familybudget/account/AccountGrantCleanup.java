package com.familybudget.account;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.family.FamilyMembershipEnded;

/**
 * Grants only exist between members of one family, so leaving it (in any way) revokes
 * them. Runs synchronously inside the family change's database transaction.
 */
@Component
class AccountGrantCleanup {

	private final AccountRepository accounts;

	AccountGrantCleanup(AccountRepository accounts) {
		this.accounts = accounts;
	}

	@EventListener
	@Transactional
	void on(FamilyMembershipEnded event) {
		this.accounts.deleteGrantsInvolving(event.userId());
	}

}
