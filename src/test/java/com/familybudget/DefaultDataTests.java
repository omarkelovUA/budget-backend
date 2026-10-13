package com.familybudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.TestUsers.Session;

/** What a new user starts with (ARCHITECTURE §3.2): common accounts and categories in their language. */
@IntegrationTest
class DefaultDataTests {

	private final MockMvcTester mvc;

	private final TestApi api;

	@Autowired
	DefaultDataTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void signUpCreatesCommonAccountsInTheDefaultCurrency() {
		Session alice = TestUsers.register(this.mvc);
		Session piotr = TestUsers.registerIn(this.mvc, "pl-PL");

		assertThat(this.api.get(alice, "/api/v1/accounts")).bodyJson().isLenientlyEqualTo("""
				[{"name": "Cash", "balance": 0, "currency": "UAH", "icon": "assets/cash.svg", "access": "owner"},
				 {"name": "Card", "balance": 0, "currency": "UAH", "icon": "assets/card.svg", "access": "owner"}]
				""");
		assertThat(this.api.get(piotr, "/api/v1/accounts")).bodyJson()
			.extractingPath("$[*].currency")
			.asArray()
			.containsExactly("PLN", "PLN");
	}

	@Test
	void signUpCreatesIncomeAndExpenseCategoriesSharedWithTheFamily() {
		MvcTestResult categories = this.api.get(TestUsers.register(this.mvc), "/api/v1/categories");

		assertThat(categories).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Cafes and restaurants", "Entertainment", "Groceries", "Health", "Home and utilities",
					"Transport", "Other income", "Salary");
		assertThat(categories).bodyJson()
			.extractingPath("$[?(@.type == 'income')].name")
			.asArray()
			.containsExactly("Other income", "Salary");
		assertThat(categories).bodyJson()
			.extractingPath("$[*].visibility")
			.asArray()
			.containsOnly("shared");
	}

	@Test
	void namesFollowTheSignUpLanguage() {
		Session olena = TestUsers.registerIn(this.mvc, "uk-UA");

		assertThat(this.api.get(olena, "/api/v1/accounts")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Готівка", "Картка");
		assertThat(this.api.get(olena, "/api/v1/categories")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.contains("Продукти", "Зарплата", "Інші доходи");
	}

	@Test
	void theDefaultsAreOrdinaryData() {
		Session alice = TestUsers.register(this.mvc);
		UUID cash = UUID.fromString(TestUsers.json(this.api.get(alice, "/api/v1/accounts"), "$[0].id"));

		assertThat(this.api.put(alice, "/api/v1/accounts/" + cash, """
				{"name": "Wallet", "balance": 250, "currency": "UAH", "icon": "assets/wallet.svg", "color": "red"}
				""")).hasStatusOk();
		this.api.clearDefaults(alice);

		assertThat(this.api.get(alice, "/api/v1/accounts")).bodyJson().extractingPath("$.length()").isEqualTo(0);
		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson().extractingPath("$.length()").isEqualTo(0);
		assertThat(this.api.get(alice, "/api/v1/accounts/{id}", cash)).hasStatus(HttpStatus.NOT_FOUND);
	}

}
