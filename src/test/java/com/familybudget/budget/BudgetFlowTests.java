package com.familybudget.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestApi;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

/** Budgets and how factAmount is derived from payments (ARCHITECTURE.md §3.1). */
@IntegrationTest
class BudgetFlowTests {

	private final TestApi api;

	private final MockMvcTester mvc;

	@Autowired
	BudgetFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void sharedBudgetCountsEveryMembersPaymentsAndPrivateOnlyTheOwners() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID groceries = this.api.category(alice, "expense", "Groceries", "shared");
		UUID alicesCard = this.api.account(alice, "Alice card", "1000", "private");
		UUID bobsCard = this.api.account(bob, "Bob card", "1000", "private");
		this.api.pay(alice, alicesCard, groceries, "100", "2026-10-01");
		this.api.pay(bob, bobsCard, groceries, "50", "2026-10-31");
		this.api.pay(bob, bobsCard, groceries, "999", "2026-11-01");

		MvcTestResult shared = budget(alice, groceries, "2026-10", "500", "shared");
		assertThat(shared).hasStatus(HttpStatus.CREATED);
		assertThat(shared).bodyJson().extractingPath("$.factAmount").isEqualTo(150.0);
		assertThat(shared).bodyJson().extractingPath("$.category.name").isEqualTo("Groceries");

		MvcTestResult bobsOwn = budget(bob, groceries, "2026-10", "80", "private");
		assertThat(bobsOwn).bodyJson().extractingPath("$.factAmount").isEqualTo(50.0);

		assertThat(this.api.get(alice, "/api/v1/budgets")).bodyJson()
			.extractingPath("$[*].visibility")
			.asArray()
			.containsExactly("shared");
		assertThat(this.api.get(bob, "/api/v1/budgets")).bodyJson().extractingPath("$.length()").isEqualTo(2);
	}

	@Test
	void factAmountFollowsTransactionsAsTheyChange() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "1000", "private");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		budget(alice, food, "2026-10", "300", "private");
		UUID payment = TestApi.id(this.api.pay(alice, card, food, "120", "2026-10-15"));

		assertThat(this.api.get(alice, "/api/v1/budgets?period=2026-10")).bodyJson()
			.extractingPath("$[0].factAmount")
			.isEqualTo(120.0);

		this.api.delete(alice, "/api/v1/transactions/" + payment);
		assertThat(this.api.get(alice, "/api/v1/budgets?period=2026-10")).bodyJson()
			.extractingPath("$[0].factAmount")
			.isEqualTo(0);
		assertThat(this.api.get(alice, "/api/v1/budgets?period=2026-09")).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(0);
	}

	@Test
	void sharedBudgetNeedsASharedCategory() {
		Session alice = TestUsers.register(this.mvc);
		this.api.family(alice);
		UUID hobby = this.api.category(alice, "expense", "Hobby", "private");

		assertThat(budget(alice, hobby, "2026-10", "100", "shared")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "category-not-shared");
	}

	@Test
	void oneBudgetPerCategoryAndMonth() {
		Session alice = TestUsers.register(this.mvc);
		UUID food = this.api.category(alice, "expense", "Food", "private");
		budget(alice, food, "2026-10", "100", "private");

		assertThat(budget(alice, food, "2026-10", "200", "private")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "budget-exists");
		assertThat(budget(alice, food, "2026-11", "200", "private")).hasStatus(HttpStatus.CREATED);
		assertThat(budget(alice, food, "2026-13", "200", "private")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void updateAndDelete() {
		Session alice = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		UUID food = this.api.category(alice, "expense", "Food", "private");
		UUID budget = TestApi.id(budget(alice, food, "2026-10", "100", "private"));

		assertThat(this.api.put(alice, "/api/v1/budgets/" + budget, """
				{"categoryId": "%s", "period": "2026-10", "plannedAmount": 250, "visibility": "private"}
				""".formatted(food))).hasStatusOk().bodyJson().extractingPath("$.plannedAmount").isEqualTo(250.00);
		assertThat(this.api.delete(stranger, "/api/v1/budgets/" + budget)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.delete(alice, "/api/v1/budgets/" + budget)).hasStatus(HttpStatus.NO_CONTENT);
	}

	private MvcTestResult budget(Session session, UUID categoryId, String period, String planned, String visibility) {
		return this.api.post(session, "/api/v1/budgets", """
				{"categoryId": "%s", "period": "%s", "plannedAmount": %s, "visibility": "%s"}
				""".formatted(categoryId, period, planned, visibility));
	}

}
