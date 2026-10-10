package com.familybudget.category;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.Visibility;
import com.familybudget.user.UserRegistered;

/**
 * A new user starts with common income and expense categories, named in their language.
 * They're private (the user is alone in their family at sign-up) and ordinary: the user
 * changes or deletes them freely.
 */
@Component
class DefaultCategorySetup {

	private record Default(CategoryType type, String en, String uk, String icon, String color) {
	}

	private static final List<Default> DEFAULTS = List.of(
			new Default(CategoryType.EXPENSE, "Groceries", "Продукти", "food", "green"),
			new Default(CategoryType.EXPENSE, "Cafes and restaurants", "Кафе й ресторани", "restaurant", "yellow"),
			new Default(CategoryType.EXPENSE, "Transport", "Транспорт", "transport", "blue"),
			new Default(CategoryType.EXPENSE, "Home and utilities", "Дім і комунальні", "home", "yellow"),
			new Default(CategoryType.EXPENSE, "Health", "Здоров'я", "health", "red"),
			new Default(CategoryType.EXPENSE, "Entertainment", "Розваги", "cinema", "blue"),
			new Default(CategoryType.INCOME, "Salary", "Зарплата", "salary", "green"),
			new Default(CategoryType.INCOME, "Other income", "Інші доходи", "other", "blue"));

	private final CategoryService categoryService;

	DefaultCategorySetup(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@EventListener
	@Transactional
	void on(UserRegistered event) {
		boolean ukrainian = "uk".equals(event.language());
		for (Default category : DEFAULTS) {
			this.categoryService.create(event.userId(), new CategoryRequest(category.type(),
					ukrainian ? category.uk() : category.en(), "assets/" + category.icon() + ".svg", category.color(),
					Visibility.PRIVATE));
		}
	}

}
