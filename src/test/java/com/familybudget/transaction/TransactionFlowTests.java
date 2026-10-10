package com.familybudget.transaction;

import static com.familybudget.TestApi.shared;
import static com.familybudget.TestApi.visible;
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

/** Balance effects and the derived visibility of transactions (ARCHITECTURE.md §3.2). */
@IntegrationTest
class TransactionFlowTests {

	private final TestApi api;

	private final MockMvcTester mvc;

	@Autowired
	TransactionFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void paymentsMoveTheBalanceByCategoryType() {
		Session alice = TestUsers.register(this.mvc);
		this.api.clearDefaults(alice);
		UUID card = this.api.account(alice, "Card", "100");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		UUID salary = this.api.category(alice, "income", "Salary", "private");

		MvcTestResult payment = this.api.pay(alice, card, food, "30.25", "2026-10-02");
		assertThat(payment).hasStatus(HttpStatus.CREATED);
		assertThat(payment).bodyJson().extractingPath("$.kind").isEqualTo("payment");
		assertThat(payment).bodyJson().extractingPath("$.account.name").isEqualTo("Card");
		assertThat(payment).bodyJson().extractingPath("$.category.type").isEqualTo("expense");
		assertThat(payment).bodyJson().extractingPath("$.recordedBy").isEqualTo(alice.userId().toString());
		assertThat(payment).bodyJson().doesNotHavePath("$.fromAccount");
		assertThat(payment).bodyJson().doesNotHavePath("$.account.balance");

		this.api.pay(alice, card, salary, "1000", "2026-10-03");

		assertThat(this.api.balance(alice, card)).isEqualByComparingTo("1069.75");
	}

	@Test
	void transfersMoveMoneyBetweenAccountsInTheSameCurrency() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "100");
		UUID savings = this.api.account(alice, "Savings", "0");
		UUID dollars = TestApi.id(this.api.createAccount(alice, "USD", "0", "USD"));

		MvcTestResult transfer = this.api.transfer(alice, card, savings, "40");
		assertThat(transfer).hasStatus(HttpStatus.CREATED);
		assertThat(transfer).bodyJson().extractingPath("$.fromAccount.name").isEqualTo("Card");
		assertThat(transfer).bodyJson().doesNotHavePath("$.account");
		assertThat(this.api.balance(alice, card)).isEqualByComparingTo("60.00");
		assertThat(this.api.balance(alice, savings)).isEqualByComparingTo("40.00");

		assertThat(this.api.transfer(alice, card, dollars, "10")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "currency-mismatch");
		assertThat(this.api.transfer(alice, card, card, "10")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void deletingATransactionReversesItsEffect() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "100");
		UUID savings = this.api.account(alice, "Savings", "0");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		UUID payment = TestApi.id(this.api.pay(alice, card, food, "30", "2026-10-02"));
		UUID transfer = TestApi.id(this.api.transfer(alice, card, savings, "20"));

		assertThat(this.api.delete(alice, "/api/v1/transactions/" + payment)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.delete(alice, "/api/v1/transactions/" + transfer)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.api.balance(alice, card)).isEqualByComparingTo("100.00");
		assertThat(this.api.balance(alice, savings)).isEqualByComparingTo("0.00");
	}

	@Test
	void membersSeeTransactionsOnAccountsSharedWithThem() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID card = this.api.account(alice, "Alice card", "100");
		UUID cash = this.api.account(alice, "Family cash", "100", visible(bob));
		UUID food = this.api.category(alice, "expense", "Food", "shared");
		this.api.pay(alice, card, food, "1", "2026-10-01");
		this.api.pay(alice, cash, food, "2", "2026-10-02");

		assertThat(this.api.get(alice, "/api/v1/transactions")).bodyJson()
			.extractingPath("$.content[*].amount")
			.asArray()
			.containsExactly(2.0, 1.0);
		MvcTestResult bobsView = this.api.get(bob, "/api/v1/transactions");
		assertThat(bobsView).bodyJson().extractingPath("$.content[*].amount").asArray().containsExactly(2.0);
		assertThat(bobsView).bodyJson().extractingPath("$.content[0].account.access").isEqualTo("visible");
		assertThat(bobsView).bodyJson().extractingPath("$.content[0].editable").isEqualTo(false);
		assertThat(bobsView).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
	}

	@Test
	void transferFromAPrivateIntoASharedAccountIsVisibleButNotEditableByTheFamily() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID card = this.api.account(alice, "Alice card", "100");
		UUID cash = this.api.account(alice, "Family cash", "0", shared(bob));
		UUID transfer = TestApi.id(this.api.transfer(alice, card, cash, "25"));

		MvcTestResult bobsView = this.api.get(bob, "/api/v1/transactions");
		assertThat(bobsView).bodyJson().extractingPath("$.content[0].fromAccount.name").isEqualTo("Alice card");
		assertThat(bobsView).bodyJson().extractingPath("$.content[0].fromAccount.access").isEqualTo("none");
		assertThat(bobsView).bodyJson().extractingPath("$.content[0].editable").isEqualTo(false);

		assertThat(this.api.delete(bob, "/api/v1/transactions/" + transfer)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "transaction-not-editable");
		assertThat(this.api.delete(alice, "/api/v1/transactions/" + transfer)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void onlyTheRecorderOrTheOwnerDeletesATransaction() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		this.api.family(alice, bob, carol);
		UUID joint = this.api.account(alice, "Joint", "100", shared(bob), shared(carol));
		UUID food = this.api.category(alice, "expense", "Food", "shared");
		UUID first = TestApi.id(this.api.pay(bob, joint, food, "1", "2026-10-01"));
		UUID second = TestApi.id(this.api.pay(bob, joint, food, "2", "2026-10-02"));

		assertThat(this.api.get(bob, "/api/v1/transactions")).bodyJson()
			.extractingPath("$.content[*].editable")
			.asArray()
			.containsExactly(true, true);
		assertThat(this.api.get(carol, "/api/v1/transactions")).bodyJson()
			.extractingPath("$.content[*].editable")
			.asArray()
			.containsExactly(false, false);
		// Carol has shared access too, but neither recorded it nor owns the account.
		assertThat(this.api.delete(carol, "/api/v1/transactions/" + first)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "transaction-not-editable");
		// The owner can delete what others recorded on her account.
		assertThat(this.api.delete(alice, "/api/v1/transactions/" + first)).hasStatus(HttpStatus.NO_CONTENT);

		// Once Bob is only allowed to look, he can't delete his own record either.
		this.api.updateAccount(alice, joint, "Joint", "97", visible(bob), shared(carol));
		assertThat(this.api.delete(bob, "/api/v1/transactions/" + second)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void cantUseSomeoneElsesPrivateAccountOrCategory() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID alicesCard = this.api.account(alice, "Alice card", "100");
		UUID alicesHobby = this.api.category(alice, "expense", "Hobby", "private");
		UUID bobsCard = this.api.account(bob, "Bob card", "100");
		UUID bobsFood = this.api.category(bob, "expense", "Food", "private");

		assertThat(this.api.pay(bob, alicesCard, bobsFood, "1", "2026-10-01")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.pay(bob, bobsCard, alicesHobby, "1", "2026-10-01")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.transfer(bob, alicesCard, bobsCard, "1")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.get(bob, "/api/v1/transactions?accountId=" + alicesCard)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.balance(alice, alicesCard)).isEqualByComparingTo("100.00");
	}

	@Test
	void rejectsMalformedTransactions() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "100");
		UUID food = this.api.category(alice, "expense", "Food", "private");

		assertThat(this.api.pay(alice, card, food, "0", "2026-10-01")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.api.pay(alice, card, food, "-5", "2026-10-01")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.api.post(alice, "/api/v1/transactions", """
				{"kind": "payment", "amount": 1, "date": "2026-10-01", "accountId": "%s"}
				""".formatted(card))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "invalid-transaction");
	}

	@Test
	void monthSummaryCountsVisiblePaymentsPerCurrencyAndSkipsTransfers() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		this.api.clearDefaults(alice);
		UUID alicesCard = this.api.account(alice, "Alice card", "1000");
		UUID cash = this.api.account(alice, "Family cash", "1000", shared(bob));
		UUID dollars = TestApi.id(this.api.createAccount(alice, "USD", "1000", "USD", visible(bob)));
		UUID food = this.api.category(alice, "expense", "Food", "shared");
		UUID salary = this.api.category(alice, "income", "Salary", "shared");
		this.api.pay(alice, alicesCard, food, "10", "2026-10-01");
		this.api.pay(alice, cash, food, "20", "2026-10-31");
		this.api.pay(alice, cash, salary, "500", "2026-10-15");
		this.api.pay(alice, dollars, food, "7", "2026-10-15");
		this.api.pay(alice, cash, food, "99", "2026-11-01");
		this.api.transfer(alice, alicesCard, cash, "300");

		assertThat(this.api.get(alice, "/api/v1/transactions/summary?period=2026-10")).bodyJson()
			.isLenientlyEqualTo("""
					[{"currency": "UAH", "income": 500.00, "expense": 30.00},
					 {"currency": "USD", "income": 0, "expense": 7.00}]
					""");
		// Bob can't see Alice's private card, so its payment isn't in his totals.
		assertThat(this.api.get(bob, "/api/v1/transactions/summary?period=2026-10")).bodyJson()
			.isLenientlyEqualTo("""
					[{"currency": "UAH", "income": 500.00, "expense": 20.00},
					 {"currency": "USD", "income": 0, "expense": 7.00}]
					""");
		assertThat(this.api.get(TestUsers.register(this.mvc), "/api/v1/transactions/summary?period=2026-10"))
			.bodyJson()
			.isLenientlyEqualTo("[]");
		assertThat(this.api.get(alice, "/api/v1/transactions/summary")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void listIsPagedNewestFirstAndFilterableByAccount() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "100");
		UUID other = this.api.account(alice, "Other", "100");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		this.api.pay(alice, card, food, "1", "2026-10-01");
		this.api.pay(alice, card, food, "3", "2026-10-03");
		this.api.pay(alice, card, food, "2", "2026-10-02");
		this.api.pay(alice, other, food, "9", "2026-10-09");

		MvcTestResult page = this.api.get(alice, "/api/v1/transactions?accountId=" + card + "&size=2");
		assertThat(page).bodyJson().extractingPath("$.content[*].amount").asArray().containsExactly(3.0, 2.0);
		assertThat(page).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
		assertThat(page).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
		assertThat(this.api.get(alice, "/api/v1/transactions?size=500")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
