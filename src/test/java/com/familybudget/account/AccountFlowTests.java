package com.familybudget.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestApi;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

/** Account CRUD and the private/shared visibility rule (ARCHITECTURE.md §6.3). */
@IntegrationTest
class AccountFlowTests {

	private final TestApi api;

	private final MockMvcTester mvc;

	@Autowired
	AccountFlowTests(MockMvcTester mvc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc);
	}

	@Test
	void privateAccountsAreOnlyVisibleToTheirOwner() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);

		UUID card = this.api.account(alice, "Alice card", "100.50", "private");
		UUID cash = this.api.account(alice, "Family cash", "500", "shared");

		assertThat(this.api.get(alice, "/api/v1/accounts")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Alice card", "Family cash");
		assertThat(this.api.get(bob, "/api/v1/accounts")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Family cash");
		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", card)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", cash)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.visibility")
			.isEqualTo("shared");
	}

	@Test
	void outsidersAndFormerMembersCantSeeSharedAccounts() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID cash = this.api.account(alice, "Family cash", "500", "shared");

		assertThat(this.api.get(stranger, "/api/v1/accounts/{id}", cash)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.delete(stranger, "/api/v1/accounts/" + cash)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(this.api.post(bob, "/api/v1/families/current/leave", "")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", cash)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void anyFamilyMemberCanEditASharedAccount() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID cash = this.api.account(alice, "Family cash", "500", "shared");

		MvcTestResult updated = this.api.put(bob, "/api/v1/accounts/" + cash, """
				{"name": "Jar", "balance": 450, "currency": "UAH", "icon": "jar", "color": "blue", "visibility": "shared"}
				""");

		assertThat(updated).hasStatusOk();
		assertThat(updated).bodyJson().extractingPath("$.name").isEqualTo("Jar");
		assertThat(this.api.balance(alice, cash)).isEqualByComparingTo("450.00");
	}

	@Test
	void sharingRequiresAFamily() {
		Session loner = TestUsers.register(this.mvc);

		assertThat(this.api.createAccount(loner, "Cash", "0", "UAH", "shared")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "no-family");
	}

	@Test
	void visibilityCantChangeAfterCreation() {
		Session alice = TestUsers.register(this.mvc);
		this.api.family(alice);
		UUID card = this.api.account(alice, "Card", "0", "private");

		assertThat(this.api.put(alice, "/api/v1/accounts/" + card, """
				{"name": "Card", "balance": 0, "currency": "UAH", "icon": "card", "color": "green", "visibility": "shared"}
				""")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "visibility-immutable");
	}

	@Test
	void validatesInput() {
		Session alice = TestUsers.register(this.mvc);

		assertThat(this.api.createAccount(alice, "Card", "0", "uah", "private")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.api.createAccount(alice, "Card", "1.234", "UAH", "private")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.api.createAccount(alice, "Card", "0", "UAH", "public")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void accountWithTransactionsCantBeDeleted() {
		Session alice = TestUsers.register(this.mvc);
		UUID card = this.api.account(alice, "Card", "100", "private");
		UUID empty = this.api.account(alice, "Empty", "0", "private");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		this.api.pay(alice, card, food, "10", "2026-10-01");

		assertThat(this.api.delete(alice, "/api/v1/accounts/" + card)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(TestApi.PROBLEMS + "account-in-use");
		assertThat(this.api.delete(alice, "/api/v1/accounts/" + empty)).hasStatus(HttpStatus.NO_CONTENT);
	}

}
