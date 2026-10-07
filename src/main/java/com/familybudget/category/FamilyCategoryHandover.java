package com.familybudget.category;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.family.FamilyDeleted;

/**
 * Keeps a deleted family's categories reachable: transactions still reference them, so
 * they can't be dropped, and the last member is the only one who could see them anyway.
 */
@Component
class FamilyCategoryHandover {

	private final CategoryRepository categories;

	FamilyCategoryHandover(CategoryRepository categories) {
		this.categories = categories;
	}

	@EventListener
	@Transactional
	void on(FamilyDeleted event) {
		this.categories.transferFamilyCategories(event.familyId(), event.lastMemberId());
	}

}
