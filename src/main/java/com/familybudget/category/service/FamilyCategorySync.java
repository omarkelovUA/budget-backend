package com.familybudget.category.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.family.event.FamilyMembershipStarted;

/** Someone joining a family: their personal categories that the family also has merge into it. */
@Component
public class FamilyCategorySync {

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
