package com.familybudget.family;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.user.UserRegistered;

/** Every user belongs to a family: sign-up creates theirs, with them as its only member. */
@Component
class DefaultFamilySetup {

	private final FamilyService familyService;

	DefaultFamilySetup(FamilyService familyService) {
		this.familyService = familyService;
	}

	@EventListener
	@Transactional
	void on(UserRegistered event) {
		this.familyService.createOwnFamily(event.userId(), event.language());
	}

}
