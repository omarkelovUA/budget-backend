package com.familybudget.transaction;

import static com.familybudget.TestApi.PROBLEMS;
import static com.familybudget.TestApi.shared;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
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

/** Money between family members through transient accounts (PLAN.md M14). */
@IntegrationTest
class FamilyTransferFlowTests {

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private final TestApi api;

	@Autowired
	FamilyTransferFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void joiningCreatesTransientAccountsBothWaysInEachSendersDefaultCurrency() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = registerIn("pl-PL");
		Session carol = TestUsers.register(this.mvc);
		this.api.family(alice, bob, carol);

		assertThat(transients(alice, bob)).containsExactly("UAH");
		assertThat(transients(bob, alice)).containsExactly("PLN");
		assertThat(transients(bob, carol)).containsExactly("PLN");
		assertThat(transients(carol, bob)).containsExactly("UAH");
		assertThat(transients(alice, carol)).containsExactly("UAH");
		// They never show up as accounts.
		assertThat(this.api.get(alice, "/api/v1/accounts")).bodyJson().extractingPath("$.length()").isEqualTo(0);
	}

	@Test
	void theRecipientCollectsIntoTheirOwnAccountInParts() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID alicesCard = this.api.account(alice, "Alice card", "1000");
		UUID bobsCard = this.api.account(bob, "Bob card", "0");

		assertThat(send(alice, alicesCard, bob, "300")).hasStatus(HttpStatus.CREATED);
		assertThat(this.api.balance(alice, alicesCard)).isEqualByComparingTo("700.00");

		MvcTestResult alicesView = this.api.get(alice, "/api/v1/family-transfers");
		assertThat(alicesView).bodyJson().extractingPath("$.outgoing[0].counterpart.id").isEqualTo(bob.userId().toString());
		assertThat(alicesView).bodyJson().extractingPath("$.outgoing[0].amount").isEqualTo(300.0);
		assertThat(alicesView).bodyJson().extractingPath("$.incoming.length()").isEqualTo(0);
		MvcTestResult bobsView = this.api.get(bob, "/api/v1/family-transfers");
		assertThat(bobsView).bodyJson().extractingPath("$.incoming[0].counterpart.id").isEqualTo(alice.userId().toString());
		UUID transfer = UUID.fromString(TestUsers.json(bobsView, "$.incoming[0].id"));

		assertThat(collect(bob, transfer, bobsCard, "100")).hasStatus(HttpStatus.CREATED);
		assertThat(collect(bob, transfer, bobsCard, null)).hasStatus(HttpStatus.CREATED);
		assertThat(this.api.balance(bob, bobsCard)).isEqualByComparingTo("300.00");
		assertThat(this.api.get(bob, "/api/v1/family-transfers")).bodyJson()
			.extractingPath("$.incoming.length()")
			.isEqualTo(0);
		assertProblem(collect(bob, transfer, bobsCard, "1"), HttpStatus.CONFLICT, "insufficient-in-transit");

		// Bob sees the collection on his account; Alice sees both steps through her transfer.
		assertThat(this.api.get(bob, "/api/v1/transactions?accountId=" + bobsCard)).bodyJson()
			.extractingPath("$.content[*].fromAccount.inTransit")
			.asArray()
			.containsExactly(true, true);
		assertThat(this.api.get(alice, "/api/v1/transactions")).bodyJson()
			.extractingPath("$.content[*].editable")
			.asArray()
			.containsOnly(false);
	}

	@Test
	void theSenderCanTakeBackWhatIsStillWaiting() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID alicesCard = this.api.account(alice, "Alice card", "1000");
		UUID bobsCard = this.api.account(bob, "Bob card", "0");
		send(alice, alicesCard, bob, "300");
		UUID transfer = UUID.fromString(TestUsers.json(this.api.get(alice, "/api/v1/family-transfers"), "$.outgoing[0].id"));

		assertThat(takeBack(alice, transfer, alicesCard, "50")).hasStatus(HttpStatus.CREATED);
		assertThat(this.api.balance(alice, alicesCard)).isEqualByComparingTo("750.00");
		assertProblem(takeBack(alice, transfer, alicesCard, "251"), HttpStatus.CONFLICT, "insufficient-in-transit");

		// Each side only does their own part.
		assertThat(takeBack(bob, transfer, bobsCard, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(collect(alice, transfer, alicesCard, null)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void moneyOnlyMovesBetweenOwnAccountsAndFamilyMembers() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID alicesCard = this.api.account(alice, "Alice card", "1000");
		UUID bobsJoint = this.api.account(bob, "Bob joint", "1000", shared(alice));
		UUID bobsCard = this.api.account(bob, "Bob card", "0");
		UUID bobsDollars = TestApi.id(this.api.createAccount(bob, "Bob USD", "0", "USD"));

		assertProblem(send(alice, bobsJoint, bob, "1"), HttpStatus.FORBIDDEN, "not-account-owner");
		assertProblem(send(alice, alicesCard, stranger, "1"), HttpStatus.CONFLICT, "recipient-not-in-family");
		assertProblem(send(alice, alicesCard, alice, "1"), HttpStatus.CONFLICT, "recipient-not-in-family");
		assertThat(send(alice, alicesCard, bob, "0")).hasStatus(HttpStatus.BAD_REQUEST);

		send(alice, alicesCard, bob, "10");
		UUID transfer = UUID.fromString(TestUsers.json(this.api.get(bob, "/api/v1/family-transfers"), "$.incoming[0].id"));
		assertProblem(collect(bob, transfer, bobsDollars, null), HttpStatus.CONFLICT, "currency-mismatch");
		assertThat(collect(bob, transfer, alicesCard, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(collect(bob, transfer, bobsCard, null)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void otherCurrenciesGetATransientAccountOnFirstUse() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID alicesDollars = TestApi.id(this.api.createAccount(alice, "USD", "100", "USD"));

		assertThat(send(alice, alicesDollars, bob, "40")).hasStatus(HttpStatus.CREATED);

		assertThat(transients(alice, bob)).containsExactlyInAnyOrder("UAH", "USD");
		assertThat(this.api.get(bob, "/api/v1/family-transfers")).bodyJson()
			.extractingPath("$.incoming[0].currency")
			.isEqualTo("USD");
	}

	@Test
	void transientAccountsAreOffLimitsToEverythingElse() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID alicesCard = this.api.account(alice, "Alice card", "1000");
		UUID food = this.api.category(alice, "expense", "Food", "private");
		UUID sent = TestApi.id(send(alice, alicesCard, bob, "100"));
		UUID transit = UUID.fromString(TestUsers.json(this.api.get(alice, "/api/v1/family-transfers"), "$.outgoing[0].id"));

		assertProblem(this.api.pay(alice, transit, food, "1", "2026-10-01"), HttpStatus.CONFLICT, "transient-account");
		assertProblem(this.api.transfer(alice, transit, alicesCard, "1"), HttpStatus.CONFLICT, "transient-account");
		assertProblem(this.api.updateAccount(alice, transit, "Mine", "0"), HttpStatus.CONFLICT, "transient-account");
		assertProblem(this.api.closeAccount(alice, transit, null), HttpStatus.CONFLICT, "transient-account");
		assertProblem(this.api.delete(alice, "/api/v1/transactions/" + sent), HttpStatus.CONFLICT, "transient-account");
		assertProblem(this.api.closeAccount(alice, alicesCard, transit), HttpStatus.CONFLICT, "transient-account");
	}

	@Test
	void leavingWritesOffUncollectedMoneyAsTheSendersExpense() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID alicesCard = this.api.account(alice, "Alice card", "1000");
		UUID bobsCard = this.api.account(bob, "Bob card", "1000");
		send(alice, alicesCard, bob, "300");
		send(bob, bobsCard, alice, "40");

		assertThat(this.api.postEmpty(bob, "/api/v1/families/current/leave")).hasStatus(HttpStatus.NO_CONTENT);

		String thisMonth = "/api/v1/transactions/summary?period=" + YearMonth.now();
		assertThat(this.api.get(alice, thisMonth)).bodyJson().isLenientlyEqualTo("""
				[{"currency": "UAH", "income": 0, "expense": 300.00}]
				""");
		assertThat(this.api.get(bob, thisMonth)).bodyJson().isLenientlyEqualTo("""
				[{"currency": "UAH", "income": 0, "expense": 40.00}]
				""");
		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly(FamilyTransferService.WRITE_OFF_CATEGORY);
		for (Session member : List.of(alice, bob)) {
			MvcTestResult view = this.api.get(member, "/api/v1/family-transfers");
			assertThat(view).bodyJson().extractingPath("$.outgoing.length()").isEqualTo(0);
			assertThat(view).bodyJson().extractingPath("$.incoming.length()").isEqualTo(0);
		}
		assertThat(transients(alice, bob)).isEmpty();
		assertThat(this.api.balance(alice, alicesCard)).isEqualByComparingTo("700.00");
	}

	@Test
	void movingToAnotherFamilyWritesOffAndStartsFreshWithTheNewMembers() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		this.api.post(carol, "/api/v1/families", """
				{"name": "Carol's"}
				""");
		UUID alicesCard = this.api.account(alice, "Alice card", "1000");
		send(alice, alicesCard, bob, "300");

		assertThat(this.api.join(carol, bob)).hasStatusOk();

		// Closing the old transient accounts happens inside the accept; the invitation must
		// still end up accepted (i.e. no longer offered).
		assertThat(this.api.get(bob, "/api/v1/invitations")).bodyJson().extractingPath("$.length()").isEqualTo(0);
		assertThat(transients(alice, bob)).isEmpty();
		assertThat(transients(bob, carol)).containsExactly("UAH");
		assertThat(transients(carol, bob)).containsExactly("UAH");
		assertThat(this.api.balance(alice, alicesCard)).isEqualByComparingTo("700.00");
	}

	private Session registerIn(String acceptLanguage) {
		return Session.from(this.mvc.post()
			.uri("/api/v1/auth/register")
			.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "displayName": "Test User", "password": "%s"}
					""".formatted(TestUsers.uniqueEmail(), TestUsers.PASSWORD))
			.exchange());
	}

	/** Currencies of the open transient accounts from sender to recipient. */
	private List<String> transients(Session sender, Session recipient) {
		return this.jdbc.queryForList("SELECT currency FROM accounts WHERE owner_user_id = ?"
				+ " AND transient_recipient_id = ? AND closed_at IS NULL", String.class, sender.userId(),
				recipient.userId());
	}

	private MvcTestResult send(Session sender, UUID fromAccountId, Session recipient, String amount) {
		return this.api.post(sender, "/api/v1/family-transfers", """
				{"fromAccountId": "%s", "recipientId": "%s", "amount": %s}
				""".formatted(fromAccountId, recipient.userId(), amount));
	}

	private MvcTestResult collect(Session session, UUID transfer, UUID toAccountId, String amount) {
		return withdraw(session, "/api/v1/family-transfers/" + transfer + "/collect", toAccountId, amount);
	}

	private MvcTestResult takeBack(Session session, UUID transfer, UUID toAccountId, String amount) {
		return withdraw(session, "/api/v1/family-transfers/" + transfer + "/take-back", toAccountId, amount);
	}

	private MvcTestResult withdraw(Session session, String uri, UUID toAccountId, String amount) {
		return this.api.post(session, uri, (amount != null) ? """
				{"toAccountId": "%s", "amount": %s}
				""".formatted(toAccountId, amount) : """
				{"toAccountId": "%s"}
				""".formatted(toAccountId));
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
