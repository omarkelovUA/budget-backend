package com.familybudget;

import static com.familybudget.TestApi.PROBLEMS;
import static com.familybudget.TestApi.shared;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.TestUsers.Session;

/** Each user's own order of the accounts and categories they see (ARCHITECTURE §3.2). */
@IntegrationTest
class CustomOrderFlowTests {

	private final MockMvcTester mvc;

	private final TestApi api;

	@Autowired
	CustomOrderFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void accountsKeepTheUsersOwnOrderAndNewOnesComeLast() {
		Session alice = TestUsers.register(this.mvc);
		this.api.clearDefaults(alice);
		UUID card = this.api.account(alice, "Card", "0");
		UUID cash = this.api.account(alice, "Cash", "0");
		UUID savings = this.api.account(alice, "Savings", "0");

		assertThat(reorder(alice, "accounts", savings, card, cash)).hasStatus(HttpStatus.NO_CONTENT);
		this.api.account(alice, "Travel", "0");

		assertThat(names(alice, "accounts")).containsExactly("Savings", "Card", "Cash", "Travel");
	}

	@Test
	void eachMemberOrdersSharedItemsForThemselves() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		this.api.clearDefaults(alice, bob);
		UUID joint = this.api.account(alice, "Joint", "0", shared(bob));
		this.api.account(alice, "Alice card", "0");
		UUID bobsCard = this.api.account(bob, "Bob card", "0");
		UUID food = this.api.category(alice, "expense", "Food", "shared");
		UUID rent = this.api.category(alice, "expense", "Rent", "shared");

		assertThat(reorder(bob, "accounts", bobsCard, joint)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(reorder(bob, "categories", rent, food)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(names(bob, "accounts")).containsExactly("Bob card", "Joint");
		assertThat(names(bob, "categories")).containsExactly("Rent", "Food");
		// Alice's order is untouched.
		assertThat(names(alice, "accounts")).containsExactly("Joint", "Alice card");
		assertThat(names(alice, "categories")).containsExactly("Food", "Rent");
	}

	@Test
	void onlyItemsTheUserSeesCanBeOrderedOnce() {
		Session alice = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		UUID card = id(alice, "accounts", "Card");
		UUID strangersCard = id(stranger, "accounts", "Card");

		assertProblem(reorder(alice, "accounts", card, strangersCard), HttpStatus.NOT_FOUND, "not-found");
		assertProblem(reorder(alice, "accounts", card, card), HttpStatus.BAD_REQUEST, "validation-failed");
		assertProblem(reorder(alice, "categories", id(stranger, "categories", "Salary")), HttpStatus.NOT_FOUND,
				"not-found");
	}

	private MvcTestResult reorder(Session session, String list, UUID... ids) {
		String json = List.of(ids).stream().map((id) -> "\"" + id + "\"").collect(Collectors.joining(", "));
		return this.api.put(session, "/api/v1/" + list + "/order", "{\"ids\": [" + json + "]}");
	}

	private List<String> names(Session session, String list) {
		return List.of(TestUsers.json(this.api.get(session, "/api/v1/" + list), "$[*].name")
			.replaceAll("[\\[\\]\"]", "")
			.split(","));
	}

	private UUID id(Session session, String list, String name) {
		return UUID.fromString(TestUsers.json(this.api.get(session, "/api/v1/" + list),
				"$[?(@.name == '" + name + "')].id").replaceAll("[\\[\\]\"]", ""));
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
