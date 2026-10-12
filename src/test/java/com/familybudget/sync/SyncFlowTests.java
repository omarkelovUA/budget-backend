package com.familybudget.sync;

import static com.familybudget.TestApi.PROBLEMS;
import static com.familybudget.TestApi.shared;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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

/** The mobile app's change feed (ARCHITECTURE.md §6.5). */
@IntegrationTest
class SyncFlowTests {

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private final TestApi api;

	@Autowired
	SyncFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void withoutACursorTheClientStartsWithAFullDownload() {
		Session alice = TestUsers.register(this.mvc);

		MvcTestResult result = this.api.get(alice, "/api/v1/sync/changes");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.resetRequired").isEqualTo(true);
		assertThat(TestUsers.json(result, "$.cursor")).containsOnlyDigits();
		assertThat(result).bodyJson().extractingPath("$.accounts").asArray().isEmpty();
	}

	@Test
	void newAccountAppearsOnceInTheFeed() {
		Session alice = TestUsers.register(this.mvc);
		String cursor = start(alice);
		UUID account = this.api.account(alice, "Card", "100");

		MvcTestResult first = changes(alice, cursor);

		assertThat(first).bodyJson().extractingPath("$.resetRequired").isEqualTo(false);
		assertThat(first).bodyJson().extractingPath("$.accounts[*].id").asArray().contains(account.toString());
		assertThat(changes(alice, TestUsers.json(first, "$.cursor"))).bodyJson()
			.extractingPath("$.accounts")
			.asArray()
			.isEmpty();
	}

	@Test
	void paymentResendsTheAccountWithItsNewBalance() {
		Session alice = TestUsers.register(this.mvc);
		UUID account = this.api.account(alice, "Card", "100");
		UUID category = this.api.category(alice, "expense", "Food-" + UUID.randomUUID(), "private");
		String cursor = start(alice);

		assertThat(this.api.pay(alice, account, category, "30", "2026-10-05")).hasStatus(HttpStatus.CREATED);

		assertThat(changes(alice, cursor)).bodyJson()
			.extractingPath("$.accounts[?(@.id == '%s')].balance".formatted(account))
			.asArray()
			.containsExactly(70.0);
	}

	@Test
	void deletedAccountIsReported() {
		Session alice = TestUsers.register(this.mvc);
		UUID account = this.api.account(alice, "Card", "0");
		String cursor = start(alice);

		assertThat(this.api.delete(alice, "/api/v1/accounts/{id}", account)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult result = changes(alice, cursor);
		assertThat(result).bodyJson().extractingPath("$.deleted.accounts").asArray().contains(account.toString());
		assertThat(result).bodyJson().extractingPath("$.accounts[*].id").asArray().doesNotContain(account.toString());
	}

	@Test
	void closedSharedAccountLeavesTheGranteesFeed() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID account = this.api.account(alice, "Joint", "0", shared(bob));
		UUID target = this.api.account(alice, "Cash", "0");
		String cursor = start(bob);

		assertThat(this.api.closeAccount(alice, account, target)).hasStatusOk();

		MvcTestResult result = changes(bob, cursor);
		if (!Boolean.parseBoolean(TestUsers.json(result, "$.resetRequired"))) {
			assertThat(result).bodyJson().extractingPath("$.deleted.accounts").asArray().contains(account.toString());
		}
		assertThat(result).bodyJson().extractingPath("$.accounts[*].id").asArray().doesNotContain(account.toString());
	}

	@Test
	void othersPrivateAccountsNeverAppear() throws Exception {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		String cursor = start(alice);
		UUID account = this.api.account(bob, "Secret", "0");
		assertThat(this.api.delete(bob, "/api/v1/accounts/{id}", account)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult result = changes(alice, cursor);

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(account.toString());
	}

	@Test
	void garbageCursorIsRejected() {
		Session alice = TestUsers.register(this.mvc);

		MvcTestResult result = changes(alice, "abc");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-sync-cursor");
	}

	@Test
	void tooManyChangesForceAReset() {
		Session alice = TestUsers.register(this.mvc);
		String cursor = start(alice);
		List<Object[]> rows = new ArrayList<>();
		for (int i = 0; i < 1001; i++) {
			rows.add(new Object[] { UUID.randomUUID(), alice.userId() });
		}
		this.jdbc.batchUpdate("INSERT INTO accounts (id, owner_user_id, name, balance, currency, icon, color,"
				+ " created_at) VALUES (?, ?, 'Bulk', 0, 'UAH', 'card', 'green', now())", rows);

		assertThat(changes(alice, cursor)).bodyJson().extractingPath("$.resetRequired").isEqualTo(true);
	}

	@Test
	void newCategoryTransactionAndBudgetAppear() {
		Session alice = TestUsers.register(this.mvc);
		String cursor = start(alice);
		UUID account = this.api.account(alice, "Card", "100");
		UUID category = this.api.category(alice, "expense", "Food-" + UUID.randomUUID(), "private");
		UUID payment = TestApi.id(this.api.pay(alice, account, category, "30", "2026-10-05"));
		UUID budget = TestApi.id(this.api.post(alice, "/api/v1/budgets", """
				{"categoryId": "%s", "period": "2026-10", "plannedAmount": 50, "visibility": "private"}
				""".formatted(category)));

		MvcTestResult result = changes(alice, cursor);

		assertThat(result).bodyJson().extractingPath("$.categories[*].id").asArray().contains(category.toString());
		assertThat(result).bodyJson().extractingPath("$.transactions[*].id").asArray().contains(payment.toString());
		assertThat(result).bodyJson().extractingPath("$.budgets[*].id").asArray().contains(budget.toString());
		assertThat(result).bodyJson().extractingPath("$.accounts[*].id").asArray().contains(account.toString());
	}

	@Test
	void deletedTransactionIsReportedAndTheBalanceRestored() {
		Session alice = TestUsers.register(this.mvc);
		UUID account = this.api.account(alice, "Card", "100");
		UUID category = this.api.category(alice, "expense", "Food-" + UUID.randomUUID(), "private");
		UUID payment = TestApi.id(this.api.pay(alice, account, category, "30", "2026-10-05"));
		String cursor = start(alice);

		assertThat(this.api.delete(alice, "/api/v1/transactions/{id}", payment)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult result = changes(alice, cursor);
		assertThat(result).bodyJson().extractingPath("$.deleted.transactions").asArray().contains(payment.toString());
		assertThat(result).bodyJson()
			.extractingPath("$.accounts[?(@.id == '%s')].balance".formatted(account))
			.asArray()
			.containsExactly(100.0);
	}

	@Test
	void hiddenFamilyCategoryLeavesTheMembersFeed() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID category = this.api.category(bob, "expense", "Hobby-" + UUID.randomUUID(), "shared");
		String alices = start(alice);
		String bobs = start(bob);

		assertThat(this.api.postEmpty(alice, "/api/v1/categories/{id}/hide", category)).hasStatusOk();

		assertThat(changes(alice, alices)).bodyJson()
			.extractingPath("$.categories[*].id")
			.asArray()
			.contains(category.toString());
		assertThat(changes(bob, bobs)).bodyJson()
			.extractingPath("$.deleted.categories")
			.asArray()
			.contains(category.toString());
	}

	@Test
	void deletedCategoryIsReported() {
		Session alice = TestUsers.register(this.mvc);
		UUID category = this.api.category(alice, "expense", "Gone-" + UUID.randomUUID(), "private");
		String cursor = start(alice);

		assertThat(this.api.delete(alice, "/api/v1/categories/{id}", category)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(changes(alice, cursor)).bodyJson()
			.extractingPath("$.deleted.categories")
			.asArray()
			.contains(category.toString());
	}

	@Test
	void othersPrivatePaymentsNeverAppear() throws Exception {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID account = this.api.account(bob, "Mine", "100");
		UUID category = this.api.category(bob, "expense", "Food-" + UUID.randomUUID(), "private");
		String cursor = start(alice);
		UUID payment = TestApi.id(this.api.pay(bob, account, category, "30", "2026-10-05"));
		assertThat(this.api.delete(bob, "/api/v1/transactions/{id}", payment)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult result = changes(alice, cursor);

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(payment.toString())
			.doesNotContain(account.toString());
	}

	/** The cursor a client starts from, right after its full download. */
	private String start(Session session) {
		MvcTestResult result = this.api.get(session, "/api/v1/sync/changes");
		assertThat(result).bodyJson().extractingPath("$.resetRequired").isEqualTo(true);
		return TestUsers.json(result, "$.cursor");
	}

	private MvcTestResult changes(Session session, String cursor) {
		return this.api.get(session, "/api/v1/sync/changes?since={since}", cursor);
	}

}
