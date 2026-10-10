package com.familybudget.family;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.user.UserRegistered;

/** Every user belongs to a family: sign-up creates theirs, with them as its only member. */
@Component
public class DefaultFamilySetup {

	/** Listeners that need the family run after this one. */
	public static final int ORDER = 0;

	private final FamilyService familyService;

	DefaultFamilySetup(FamilyService familyService) {
		this.familyService = familyService;
	}

	@EventListener
	@Order(ORDER)
	@Transactional
	void on(UserRegistered event) {
		this.familyService.createOwnFamily(event.userId(), event.language());
	}

}
