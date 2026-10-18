package com.familybudget.family;

import static com.familybudget.TestApi.PROBLEMS;
import static com.familybudget.TestApi.shared;
import static com.familybudget.TestApi.visible;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestApi;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

/**
 * Adding members needs an admin's subscription; without one a family with members is
 * frozen: shared data is read-only, private data isn't touched (ARCHITECTURE.md §3.4).
 */
@IntegrationTest
class FamilySubscriptionFlowTests {

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private final TestApi api;

	@Autowired
	FamilySubscriptionFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void invitingNeedsASubscription() {
		Session alice = TestUsers.register(this.mvc);

		assertProblem(this.api.invite(alice, TestUsers.uniqueEmail()), HttpStatus.FORBIDDEN, "subscription-required");
		this.api.subscribe(alice);
		assertThat(this.api.invite(alice, TestUsers.uniqueEmail())).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void acceptingNeedsTheInvitingFamilyToStillBeEntitled() {
		Session alice = this.api.subscribe(TestUsers.register(this.mvc));
		Session bob = TestUsers.register(this.mvc);
		UUID invitationId = TestApi.id(this.api.invite(alice, bob.email()));
		unsubscribe(alice);

		assertProblem(accept(bob, invitationId), HttpStatus.FORBIDDEN, "subscription-required");
		this.api.subscribe(alice);
		assertThat(accept(bob, invitationId)).hasStatusOk();
	}

	@Test
	void approvingAJoinRequestNeedsASubscription() {
		Session alice = TestUsers.register(this.mvc);
		Session dave = TestUsers.register(this.mvc);
		UUID requestId = TestApi.id(this.api.post(dave, "/api/v1/join-requests", """
				{"email": "%s"}
				""".formatted(alice.email())));

		assertProblem(approve(alice, requestId), HttpStatus.FORBIDDEN, "subscription-required");
		this.api.subscribe(alice);
		assertThat(approve(alice, requestId)).hasStatusOk();
	}

	@Test
	void onlyAnAdminsSubscriptionCounts() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		unsubscribe(alice);
		this.api.subscribe(bob);

		assertThat(family(bob)).bodyJson().isLenientlyEqualTo("""
				{"entitled": false, "frozen": true}
				""");
		assertProblem(this.api.invite(alice, TestUsers.uniqueEmail()), HttpStatus.FORBIDDEN, "subscription-required");
		assertThat(this.mvc.patch()
			.uri("/api/v1/families/current/members/{id}", bob.userId())
			.header(HttpHeaders.AUTHORIZATION, alice.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"role": "admin"}
					""")).hasStatusOk();
		assertThat(family(alice)).bodyJson().isLenientlyEqualTo("""
				{"entitled": true, "frozen": false}
				""");
	}

	@Test
	void theFamilyViewTellsWhetherItIsEntitledAndFrozen() {
		Session alice = TestUsers.register(this.mvc);
		assertThat(family(alice)).bodyJson().isLenientlyEqualTo("""
				{"entitled": false, "frozen": false}
				""");

		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		assertThat(family(bob)).bodyJson().isLenientlyEqualTo("""
				{"entitled": true, "frozen": false}
				""");

		unsubscribe(alice);
		assertThat(family(bob)).bodyJson().isLenientlyEqualTo("""
				{"entitled": false, "frozen": true}
				""");
	}

	@Test
	void aFrozenFamilysSharedDataIsReadOnly() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID joint = this.api.account(alice, "Joint", "100", shared(bob));
		UUID savings = this.api.account(alice, "Savings", "100", visible(bob));
		UUID bobsCash = this.api.account(bob, "Bob's cash", "100");
		UUID pets = this.api.category(alice, "expense", "Pets", "shared");
		UUID budget = TestApi.id(this.api.post(alice, "/api/v1/budgets", budgetJson(pets, "shared")));
		UUID bobsPayment = TestApi.id(this.api.pay(bob, joint, pets, "5", "2026-10-05"));
		unsubscribe(alice);

		// Family categories and shared budgets.
		assertFrozen(this.api.createCategory(bob, "expense", "Toys", "shared"));
		assertFrozen(this.api.put(alice, "/api/v1/categories/" + pets, """
				{"type": "expense", "name": "Animals", "icon": "food", "color": "red", "visibility": "shared"}
				"""));
		assertFrozen(this.api.delete(alice, "/api/v1/categories/{id}", pets));
		assertFrozen(this.api.postEmpty(alice, "/api/v1/categories/{id}/hide", pets));
		assertFrozen(this.api.post(alice, "/api/v1/budgets", budgetJson(this.api.category(alice, "expense", "Car",
				"private"), "shared")));
		assertFrozen(this.api.put(alice, "/api/v1/budgets/" + budget, budgetJson(pets, "shared")));
		assertFrozen(this.api.delete(alice, "/api/v1/budgets/{id}", budget));
		// Someone else's accounts, and sharing more.
		assertFrozen(this.api.pay(bob, joint, pets, "5", "2026-10-06"));
		assertFrozen(this.api.delete(bob, "/api/v1/transactions/{id}", bobsPayment));
		assertThat(this.api.get(bob, "/api/v1/transactions?accountId={id}", joint)).bodyJson()
			.extractingPath("$.content[0].editable")
			.isEqualTo(false);
		assertFrozen(this.api.updateAccount(alice, savings, "Savings", "100", shared(bob)));
		assertFrozen(this.api.createAccount(alice, "New", "0", "UAH", visible(bob)));
		assertFrozen(this.api.post(alice, "/api/v1/family-transfers", """
				{"fromAccountId": "%s", "recipientId": "%s", "amount": 10}
				""".formatted(joint, bob.userId())));

		// Private data, own accounts (family categories included) and sharing less still work.
		assertThat(this.api.createCategory(bob, "expense", "Toys", "private")).hasStatus(HttpStatus.CREATED);
		assertThat(this.api.pay(bob, bobsCash, pets, "5", "2026-10-06")).hasStatus(HttpStatus.CREATED);
		assertThat(this.api.pay(alice, joint, pets, "5", "2026-10-06")).hasStatus(HttpStatus.CREATED);
		assertThat(this.api.updateAccount(alice, joint, "Joint account", "100", shared(bob))).hasStatusOk();
		assertThat(this.api.updateAccount(alice, savings, "Savings", "100")).hasStatusOk();
		// And reading shared data.
		assertThat(this.api.get(bob, "/api/v1/accounts/{id}", joint)).hasStatusOk();
		assertThat(this.api.get(bob, "/api/v1/budgets")).hasStatusOk();
	}

	@Test
	void subscribingOrBeingAloneAgainUnfreezes() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		unsubscribe(alice);
		assertFrozen(this.api.createCategory(alice, "expense", "Toys", "shared"));

		this.api.subscribe(alice);
		assertThat(this.api.createCategory(alice, "expense", "Toys", "shared")).hasStatus(HttpStatus.CREATED);

		unsubscribe(alice);
		assertThat(this.api.postEmpty(bob, "/api/v1/families/current/leave")).hasStatusOk();
		assertThat(family(alice)).bodyJson().extractingPath("$.frozen").isEqualTo(false);
		assertThat(this.api.createCategory(alice, "expense", "Books", "shared")).hasStatus(HttpStatus.CREATED);
	}

	private void unsubscribe(Session session) {
		this.jdbc.update("DELETE FROM subscriptions WHERE user_id = ?", session.userId());
	}

	private MvcTestResult family(Session session) {
		return this.api.get(session, "/api/v1/families/current");
	}

	private MvcTestResult accept(Session session, UUID invitationId) {
		return this.api.postEmpty(session, "/api/v1/invitations/{id}/accept", invitationId);
	}

	private MvcTestResult approve(Session admin, UUID requestId) {
		return this.api.postEmpty(admin, "/api/v1/families/current/join-requests/{id}/approve", requestId);
	}

	private static String budgetJson(UUID categoryId, String visibility) {
		return """
				{"categoryId": "%s", "period": "2026-10", "plannedAmount": 50, "visibility": "%s"}
				""".formatted(categoryId, visibility);
	}

	private static void assertFrozen(MvcTestResult result) {
		assertProblem(result, HttpStatus.FORBIDDEN, "family-frozen");
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
