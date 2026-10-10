package com.familybudget.category;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.family.FamilyMembershipStarted;

/** Someone joining a family: their personal categories that the family also has merge into it. */
@Component
class FamilyCategorySync {

	private final CategoryService categoryService;

	FamilyCategorySync(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@EventListener
	@Transactional
	void on(FamilyMembershipStarted event) {
		this.categoryService.mergeDuplicates(event.familyId());
	}

}
