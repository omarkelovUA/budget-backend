package com.familybudget.account;

import static com.familybudget.TestApi.PROBLEMS;
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

/** Owner + per-member grants (ARCHITECTURE.md §3.2, §6.3) and closing accounts. */
@IntegrationTest
class AccountFlowTests {

	private final TestApi api;

	private final MockMvcTester mvc;

	@Autowired
	AccountFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	/** Alice owns the accounts; Bob gets shared, Carol visible, Dave nothing. */
	private record Family(Session alice, Session bob, Session carol, Session dave) {
	}

	private Family family() {
		Family family = new Family(TestUsers.register(this.mvc), TestUsers.register(this.mvc),
				TestUsers.register(this.mvc), TestUsers.register(this.mvc));
		this.api.family(family.alice(), family.bob(), family.carol(), family.dave());
		return family;
	}

	@Test
	void eachMemberSeesWhatTheOwnerGrantedThem() {
		Family f = family();
		Session stranger = TestUsers.register(this.mvc);
		this.api.account(f.alice(), "Alice card", "100.50");
		UUID joint = this.api.account(f.alice(), "Joint", "500", shared(f.bob()), visible(f.carol()));

		MvcTestResult alicesList = this.api.get(f.alice(), "/api/v1/accounts");
		assertThat(alicesList).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Alice card", "Joint");
		assertThat(alicesList).bodyJson().extractingPath("$[1].access").isEqualTo("owner");
		assertThat(alicesList).bodyJson().extractingPath("$[1].grants.length()").isEqualTo(2);

		MvcTestResult bobsView = this.api.get(f.bob(), "/api/v1/accounts/{id}", joint);
		assertThat(bobsView).hasStatusOk();
		assertThat(bobsView).bodyJson().extractingPath("$.access").isEqualTo("shared");
		assertThat(bobsView).bodyJson().extractingPath("$.balance").isEqualTo(500.0);
		assertThat(bobsView).bodyJson().extractingPath("$.owner.id").isEqualTo(f.alice().userId().toString());
		assertThat(bobsView).bodyJson().doesNotHavePath("$.grants");
		assertThat(this.api.get(f.bob(), "/api/v1/accounts")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Joint");

		assertThat(this.api.get(f.carol(), "/api/v1/accounts/{id}", joint)).bodyJson()
			.extractingPath("$.access")
			.isEqualTo("visible");
		assertThat(this.api.get(f.dave(), "/api/v1/accounts")).bodyJson().extractingPath("$.length()").isEqualTo(0);
		assertThat(this.api.get(f.dave(), "/api/v1/accounts/{id}", joint)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.get(stranger, "/api/v1/accounts/{id}", joint)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void onlyTheOwnerManagesTheAccount() {
		Family f = family();
		UUID joint = this.api.account(f.alice(), "Joint", "500", shared(f.bob()), visible(f.carol()));

		for (Session member : new Session[] { f.bob(), f.carol() }) {
			assertProblem(this.api.updateAccount(member, joint, "Mine now", "0"), HttpStatus.FORBIDDEN,
					"not-account-owner");
			assertProblem(this.api.delete(member, "/api/v1/accounts/" + joint), HttpStatus.FORBIDDEN,
					"not-account-owner");
			assertProblem(this.api.closeAccount(member, joint, null), HttpStatus.FORBIDDEN, "not-account-owner");
		}
		assertThat(this.api.updateAccount(f.dave(), joint, "Mine now", "0")).hasStatus(HttpStatus.NOT_FOUND);

		// The owner can change grants at any time: Bob drops to visible, Dave gets shared.
		MvcTestResult updated = this.api.updateAccount(f.alice(), joint, "Jar", "450", visible(f.bob()),
				shared(f.dave()));
		assertThat(updated).hasStatusOk();
		assertThat(updated).bodyJson().extractingPath("$.name").isEqualTo("Jar");
		assertThat(this.api.get(f.bob(), "/api/v1/accounts/{id}", joint)).bodyJson()
			.extractingPath("$.access")
			.isEqualTo("visible");
		assertThat(this.api.get(f.carol(), "/api/v1/accounts/{id}", joint)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.get(f.dave(), "/api/v1/accounts/{id}", joint)).bodyJson()
			.extractingPath("$.access")
			.isEqualTo("shared");
	}

	@Test
	void onlySharedAccessRecordsTransactions() {
		Family f = family();
		UUID joint = this.api.account(f.alice(), "Joint", "500", shared(f.bob()), visible(f.carol()));
		UUID bobsCard = this.api.account(f.bob(), "Bob card", "100");
		UUID carolsCard = this.api.account(f.carol(), "Carol card", "100");
		UUID bobsFood = this.api.category(f.bob(), "expense", "Food", "private");
		UUID carolsFood = this.api.category(f.carol(), "expense", "Food", "private");
		UUID davesFood = this.api.category(f.dave(), "expense", "Food", "private");

		assertThat(this.api.pay(f.bob(), joint, bobsFood, "10", "2026-10-01")).hasStatus(HttpStatus.CREATED);
		assertThat(this.api.transfer(f.bob(), bobsCard, joint, "20")).hasStatus(HttpStatus.CREATED);

		assertProblem(this.api.pay(f.carol(), joint, carolsFood, "10", "2026-10-01"), HttpStatus.FORBIDDEN,
				"account-read-only");
		// Visible means no transactions in either direction, paying in included.
		assertProblem(this.api.transfer(f.carol(), carolsCard, joint, "20"), HttpStatus.FORBIDDEN,
				"account-read-only");
		assertThat(this.api.pay(f.dave(), joint, davesFood, "10", "2026-10-01")).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(this.api.balance(f.alice(), joint)).isEqualByComparingTo("510.00");
	}

	@Test
	void grantsOnlyGoToOtherFamilyMembers() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		Session loner = TestUsers.register(this.mvc);
		this.api.family(alice, bob);

		assertProblem(this.api.createAccount(alice, "Joint", "0", "UAH", shared(stranger)), HttpStatus.CONFLICT,
				"grantee-not-in-family");
		assertProblem(this.api.createAccount(alice, "Joint", "0", "UAH", visible(alice)), HttpStatus.CONFLICT,
				"grantee-not-in-family");
		assertProblem(this.api.createAccount(loner, "Joint", "0", "UAH", shared(alice)), HttpStatus.CONFLICT,
				"no-family");
		assertThat(this.api.createAccount(loner, "Private", "0", "UAH")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void leavingTheFamilyRevokesGrantsBothWays() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		this.api.family(alice, bob, carol);
		UUID joint = this.api.account(alice, "Joint", "500", shared(bob), shared(carol));
		UUID bobsCard = this.api.account(bob, "Bob card", "100", visible(alice));

		assertThat(this.api.postEmpty(bob, "/api/v1/families/current/leave")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", joint)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.get(alice, "/api/v1/accounts/{id}", bobsCard)).hasStatus(HttpStatus.NOT_FOUND);
		// Bob's own account stays his, and Carol's grant is untouched.
		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", bobsCard)).hasStatusOk();
		assertThat(this.api.get(carol, "/api/v1/accounts/{id}", joint)).hasStatusOk();

		// Being removed by an admin revokes them too.
		assertThat(this.api.delete(alice, "/api/v1/families/current/members/{id}", carol.userId()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(carol, "/api/v1/accounts/{id}", joint)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void movingToAnotherFamilyRevokesGrants() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session other = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		this.api.post(other, "/api/v1/families", """
				{"name": "Others"}
				""");
		UUID joint = this.api.account(alice, "Joint", "500", shared(bob));

		assertThat(this.api.join(other, bob)).hasStatusOk();

		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", joint)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void closingMovesTheRemainingBalanceAndArchivesTheAccount() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID card = this.api.account(alice, "Card", "100", shared(bob));
		UUID savings = this.api.account(alice, "Savings", "10");
		UUID dollars = TestApi.id(this.api.createAccount(alice, "USD", "0", "USD"));
		UUID bobsCard = this.api.account(bob, "Bob card", "0", visible(alice));
		UUID food = this.api.category(alice, "expense", "Food", "private");

		assertProblem(this.api.closeAccount(alice, card, null), HttpStatus.CONFLICT, "close-target-required");
		assertProblem(this.api.closeAccount(alice, card, card), HttpStatus.CONFLICT, "close-target-required");
		assertProblem(this.api.closeAccount(alice, card, dollars), HttpStatus.CONFLICT, "currency-mismatch");
		assertProblem(this.api.closeAccount(alice, card, bobsCard), HttpStatus.FORBIDDEN, "account-read-only");

		MvcTestResult closed = this.api.closeAccount(alice, card, savings);
		assertThat(closed).hasStatusOk();
		assertThat(closed).bodyJson().extractingPath("$.closed").isEqualTo(true);
		assertThat(closed).bodyJson().extractingPath("$.balance").isEqualTo(0.0);
		assertThat(this.api.balance(alice, savings)).isEqualByComparingTo("110.00");

		assertThat(this.api.get(alice, "/api/v1/accounts")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.doesNotContain("Card");
		assertThat(this.api.get(alice, "/api/v1/accounts?closed=true")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Card");
		// Closing drops every grant.
		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", card)).hasStatus(HttpStatus.NOT_FOUND);

		// The closing transfer stays in history, but the closed account is frozen.
		MvcTestResult history = this.api.get(alice, "/api/v1/transactions?accountId=" + card);
		assertThat(history).bodyJson().extractingPath("$.content[0].toAccount.name").isEqualTo("Savings");
		assertThat(history).bodyJson().extractingPath("$.content[0].fromAccount.closed").isEqualTo(true);
		assertThat(history).bodyJson().extractingPath("$.content[0].editable").isEqualTo(false);
		UUID closingTransfer = UUID.fromString(TestUsers.json(history, "$.content[0].id"));
		assertProblem(this.api.delete(alice, "/api/v1/transactions/" + closingTransfer), HttpStatus.CONFLICT,
				"account-closed");
		assertProblem(this.api.pay(alice, card, food, "1", "2026-10-01"), HttpStatus.CONFLICT, "account-closed");
		assertProblem(this.api.updateAccount(alice, card, "Card", "0"), HttpStatus.CONFLICT, "account-closed");
		assertProblem(this.api.closeAccount(alice, card, savings), HttpStatus.CONFLICT, "account-closed");
	}

	@Test
	void closingANegativeBalancePullsTheDebtFromTheTarget() {
		Session alice = TestUsers.register(this.mvc);
		UUID credit = this.api.account(alice, "Credit card", "-50");
		UUID savings = this.api.account(alice, "Savings", "200");
		UUID empty = this.api.account(alice, "Empty", "0");

		assertThat(this.api.closeAccount(alice, credit, savings)).hasStatusOk();

		assertThat(this.api.balance(alice, savings)).isEqualByComparingTo("150.00");
		assertThat(this.api.balance(alice, credit)).isEqualByComparingTo("0.00");
		// A zero balance needs no target.
		assertThat(this.api.closeAccount(alice, empty, null)).hasStatusOk();
	}

	@Test
	void onlyAccountsWithoutTransactionsCanBeDeleted() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "100");
		UUID empty = this.api.account(alice, "Empty", "0");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		this.api.pay(alice, card, food, "10", "2026-10-01");

		assertProblem(this.api.delete(alice, "/api/v1/accounts/" + card), HttpStatus.CONFLICT, "account-in-use");
		assertThat(this.api.delete(alice, "/api/v1/accounts/" + empty)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void validatesInput() {
		Session alice = TestUsers.register(this.mvc);

		assertThat(this.api.createAccount(alice, "Card", "0", "uah")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.api.createAccount(alice, "Card", "1.234", "UAH")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.api.post(alice, "/api/v1/accounts", """
				{"name": "Card", "balance": 0, "currency": "UAH", "icon": "card", "color": "green",
				 "grants": [{"userId": "%s", "level": "owner"}]}
				""".formatted(alice.userId()))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
