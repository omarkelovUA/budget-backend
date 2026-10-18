package com.familybudget.family;

import static com.familybudget.TestApi.PROBLEMS;
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

/** Asking to join someone else's family; its admins answer (ARCHITECTURE.md §3.2). */
@IntegrationTest
class JoinRequestFlowTests {

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private final TestApi api;

	@Autowired
	JoinRequestFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void aRequestReachesTheFamilysAdminsWhoLetTheRequesterIn() {
		Session alice = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		this.api.family(alice, carol);
		Session dave = TestUsers.register(this.mvc);
		UUID davesOldFamily = this.api.familyId(dave);

		// Naming any member reaches the family, here a non-admin.
		MvcTestResult asked = ask(dave, carol.email().toUpperCase());
		assertThat(asked).hasStatus(HttpStatus.CREATED);
		UUID requestId = TestApi.id(asked);
		MvcTestResult mine = this.api.get(dave, "/api/v1/join-requests/mine");
		assertThat(mine).bodyJson().extractingPath("$.email").isEqualTo(carol.email());
		assertThat(mine).bodyJson().extractingPath("$.familyName").isEqualTo("Family");

		MvcTestResult incoming = this.api.get(alice, "/api/v1/families/current/join-requests");
		assertThat(incoming).bodyJson().extractingPath("$[0].id").isEqualTo(requestId.toString());
		assertThat(incoming).bodyJson().extractingPath("$[0].email").isEqualTo(dave.email());
		assertProblem(this.api.get(carol, "/api/v1/families/current/join-requests"), HttpStatus.FORBIDDEN,
				"admin-required");
		assertProblem(approve(carol, requestId), HttpStatus.FORBIDDEN, "admin-required");

		MvcTestResult approved = approve(alice, requestId);
		assertThat(approved).hasStatusOk();
		assertThat(approved).bodyJson().extractingPath("$.members.length()").isEqualTo(3);
		assertThat(approved).bodyJson().extractingPath("$.members[2].userId").isEqualTo(dave.userId().toString());
		assertThat(approved).bodyJson().extractingPath("$.members[2].role").isEqualTo("member");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM families WHERE id = ?", Integer.class,
				davesOldFamily)).isZero();
		assertThat(this.api.get(dave, "/api/v1/join-requests/mine")).hasStatus(HttpStatus.NO_CONTENT);
		assertProblem(approve(alice, requestId), HttpStatus.NOT_FOUND, "invalid-join-request");
	}

	@Test
	void onlySomeoneAloneWithAVerifiedEmailMayAskOnce() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		Session dave = TestUsers.register(this.mvc);

		assertProblem(ask(this.api.unlinkGoogle(TestUsers.register(this.mvc)), alice.email()), HttpStatus.FORBIDDEN,
				"email-not-verified");
		assertProblem(ask(bob, dave.email()), HttpStatus.CONFLICT, "not-alone");
		assertProblem(ask(dave, TestUsers.uniqueEmail()), HttpStatus.NOT_FOUND, "user-not-found");
		assertProblem(ask(dave, dave.email()), HttpStatus.CONFLICT, "already-member");

		assertThat(ask(dave, alice.email())).hasStatus(HttpStatus.CREATED);
		assertProblem(ask(dave, alice.email()), HttpStatus.CONFLICT, "already-requested");

		assertThat(this.api.delete(dave, "/api/v1/join-requests/mine")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(dave, "/api/v1/join-requests/mine")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(alice, "/api/v1/families/current/join-requests")).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(0);
		assertProblem(this.api.delete(dave, "/api/v1/join-requests/mine"), HttpStatus.NOT_FOUND,
				"invalid-join-request");
		// Cancelling frees the requester to ask again.
		assertThat(ask(dave, alice.email())).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void declinedRequestsCanNoLongerBeApproved() {
		Session alice = TestUsers.register(this.mvc);
		Session dave = TestUsers.register(this.mvc);
		UUID requestId = TestApi.id(ask(dave, alice.email()));

		assertThat(this.api.postEmpty(alice, "/api/v1/families/current/join-requests/{id}/decline", requestId))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertProblem(approve(alice, requestId), HttpStatus.NOT_FOUND, "invalid-join-request");
		assertThat(this.api.get(dave, "/api/v1/join-requests/mine")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(ask(dave, alice.email())).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void aRequestIsMootOnceTheRequestersFamilyGrows() {
		Session alice = TestUsers.register(this.mvc);
		Session dave = TestUsers.register(this.mvc);
		Session erin = TestUsers.register(this.mvc);
		UUID requestId = TestApi.id(ask(dave, alice.email()));

		assertThat(this.api.join(dave, erin)).hasStatusOk();

		assertThat(this.api.get(dave, "/api/v1/join-requests/mine")).hasStatus(HttpStatus.NO_CONTENT);
		assertProblem(approve(alice, requestId), HttpStatus.NOT_FOUND, "invalid-join-request");
	}

	@Test
	void approvingRespectsTheSeatLimit() {
		Session alice = TestUsers.register(this.mvc);
		Session dave = TestUsers.register(this.mvc);
		UUID requestId = TestApi.id(ask(dave, alice.email()));
		// 1 member + 9 invitations = 10 seats; requests don't reserve one.
		for (int i = 0; i < 9; i++) {
			assertThat(this.api.invite(alice, TestUsers.uniqueEmail())).hasStatus(HttpStatus.CREATED);
		}

		assertProblem(approve(alice, requestId), HttpStatus.CONFLICT, "family-full");
	}

	private MvcTestResult ask(Session session, String email) {
		return this.api.post(session, "/api/v1/join-requests", """
				{"email": "%s"}
				""".formatted(email));
	}

	private MvcTestResult approve(Session admin, UUID requestId) {
		return this.api.postEmpty(admin, "/api/v1/families/current/join-requests/{id}/approve", requestId);
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
