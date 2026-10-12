package com.familybudget.transaction.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.category.event.CategoryHiding;
import com.familybudget.category.event.CategoryMerged;
import com.familybudget.transaction.repository.TransactionRepository;

/** Keeps payments on the right category when categories merge, and guards hiding one. */
@Component
public class TransactionCategorySync {

	private final TransactionRepository transactions;

	TransactionCategorySync(TransactionRepository transactions) {
		this.transactions = transactions;
	}

	@EventListener
	@Transactional
	void on(CategoryMerged event) {
		this.transactions.moveCategory(event.from(), event.into());
	}

	/** Others' payments would point at a category they can no longer see. */
	@EventListener
	void on(CategoryHiding event) {
		if (this.transactions.existsByCategoryIdAndRecordedByNot(event.categoryId(), event.adminId())) {
			throw CategoryHiding.usedByFamily();
		}
	}

}
