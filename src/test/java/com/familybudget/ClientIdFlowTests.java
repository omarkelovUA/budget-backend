package com.familybudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.TestUsers.Session;

/**
 * Creates with a client-generated id (mobile sync, PLAN.md M29): the id is kept, and
 * sending the same create again returns the row instead of making a second one.
 */
@IntegrationTest
class ClientIdFlowTests {

	private final MockMvcTester mvc;

	private final TestApi api;

	@Autowired
	ClientIdFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void accountTakesTheClientsId() {
		Session alice = TestUsers.register(this.mvc);
		UUID id = UUID.randomUUID();

		assertThat(TestApi.id(createAccount(alice, id, "100"))).isEqualTo(id);
	}

	@Test
	void replayedAccountCreateReturnsTheSameAccount() {
		Session alice = TestUsers.register(this.mvc);
		UUID id = UUID.randomUUID();

		createAccount(alice, id, "100");
		MvcTestResult replay = createAccount(alice, id, "100");

		assertThat(TestApi.id(replay)).isEqualTo(id);
		assertThat(this.api.get(alice, "/api/v1/accounts")).bodyJson()
			.extractingPath("$[?(@.id == '%s')]".formatted(id))
			.asArray()
			.hasSize(1);
	}

	@Test
	void replayedPaymentMovesTheBalanceOnce() {
		Session alice = TestUsers.register(this.mvc);
		UUID account = TestApi.id(createAccount(alice, UUID.randomUUID(), "100"));
		UUID category = this.api.category(alice, "expense", "Food-" + UUID.randomUUID(), "private");
		UUID id = UUID.randomUUID();
		String payment = """
				{"id": "%s", "kind": "payment", "amount": 30, "date": "2026-10-05", "accountId": "%s", "categoryId": "%s"}
				""".formatted(id, account, category);

		assertThat(TestApi.id(this.api.post(alice, "/api/v1/transactions", payment))).isEqualTo(id);
		assertThat(TestApi.id(this.api.post(alice, "/api/v1/transactions", payment))).isEqualTo(id);

		assertThat(this.api.balance(alice, account)).isEqualByComparingTo(new BigDecimal("70.00"));
		assertThat(this.api.get(alice, "/api/v1/transactions?accountId={id}", account)).bodyJson()
			.extractingPath("$.content[?(@.id == '%s')]".formatted(id))
			.asArray()
			.hasSize(1);
	}

	@Test
	void replayedCategoryAndBudgetCreatesReturnTheSameRows() {
		Session alice = TestUsers.register(this.mvc);
		UUID categoryId = UUID.randomUUID();
		String category = """
				{"id": "%s", "type": "expense", "name": "Pets", "icon": "food", "color": "red", "visibility": "private"}
				""".formatted(categoryId);
		UUID budgetId = UUID.randomUUID();
		String budget = """
				{"id": "%s", "categoryId": "%s", "period": "2026-10", "plannedAmount": 50, "visibility": "private"}
				""".formatted(budgetId, categoryId);

		assertThat(TestApi.id(this.api.post(alice, "/api/v1/categories", category))).isEqualTo(categoryId);
		assertThat(TestApi.id(this.api.post(alice, "/api/v1/categories", category))).isEqualTo(categoryId);
		assertThat(TestApi.id(this.api.post(alice, "/api/v1/budgets", budget))).isEqualTo(budgetId);
		assertThat(TestApi.id(this.api.post(alice, "/api/v1/budgets", budget))).isEqualTo(budgetId);

		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson()
			.extractingPath("$[?(@.id == '%s')]".formatted(categoryId))
			.asArray()
			.hasSize(1);
		assertThat(this.api.get(alice, "/api/v1/budgets")).bodyJson()
			.extractingPath("$[?(@.id == '%s')]".formatted(budgetId))
			.asArray()
			.hasSize(1);
	}

	@Test
	void someoneElsesIdIsAConflict() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		UUID id = UUID.randomUUID();
		createAccount(alice, id, "100");

		MvcTestResult result = createAccount(bob, id, "5");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(TestApi.PROBLEMS + "id-conflict");
		assertThat(this.api.balance(alice, id)).isEqualByComparingTo(new BigDecimal("100"));
	}

	@Test
	void withoutAnIdTheServerPicksOne() {
		Session alice = TestUsers.register(this.mvc);

		assertThat(this.api.account(alice, "Cash", "10")).isNotNull();
	}

	private MvcTestResult createAccount(Session owner, UUID id, String balance) {
		return this.api.post(owner, "/api/v1/accounts", """
				{"id": "%s", "name": "Card", "balance": %s, "currency": "UAH", "icon": "card", "color": "green"}
				""".formatted(id, balance));
	}

}
