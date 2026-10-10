package com.familybudget.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.familybudget.IntegrationTest;
import com.familybudget.TestApi;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

@IntegrationTest
class CategoryFlowTests {

	private final TestApi api;

	private final MockMvcTester mvc;

	@Autowired
	CategoryFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void familySeesSharedCategoriesOnly() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		this.api.clearDefaults(alice, bob);
		this.api.category(alice, "expense", "Hobby", "private");
		this.api.category(alice, "expense", "Groceries", "shared");
		this.api.category(alice, "income", "Salary", "shared");

		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Groceries", "Hobby", "Salary");
		assertThat(this.api.get(bob, "/api/v1/categories")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Groceries", "Salary");
	}

	@Test
	void typeAndVisibilityCantChange() {
		Session alice = TestUsers.register(this.mvc);
		this.api.family(alice);
		UUID food = this.api.category(alice, "expense", "Food", "private");

		assertThat(this.api.put(alice, "/api/v1/categories/" + food, """
				{"type": "income", "name": "Food", "icon": "food", "color": "red", "visibility": "private"}
				""")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "category-type-immutable");
		assertThat(this.api.put(alice, "/api/v1/categories/" + food, """
				{"type": "expense", "name": "Food", "icon": "food", "color": "red", "visibility": "shared"}
				""")).hasStatus(HttpStatus.CONFLICT);
		assertThat(this.api.put(alice, "/api/v1/categories/" + food, """
				{"type": "expense", "name": "Eating out", "icon": "fork", "color": "orange", "visibility": "private"}
				""")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Eating out");
	}

	@Test
	void categoryInUseCantBeDeleted() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "100");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		UUID unused = this.api.category(alice, "expense", "Unused", "private");
		this.api.pay(alice, card, food, "10", "2026-10-01");

		assertThat(this.api.delete(alice, "/api/v1/categories/" + food)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "category-in-use");
		assertThat(this.api.delete(alice, "/api/v1/categories/" + unused)).hasStatus(HttpStatus.NO_CONTENT);
	}

}
