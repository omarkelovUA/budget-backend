package com.familybudget.family;

import static com.familybudget.TestApi.PROBLEMS;
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

/** Family creation, email invitations and admin-only management (ARCHITECTURE.md §3.2, §6.3). */
@IntegrationTest
class FamilyFlowTests {

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	private final TestApi api;

	@Autowired
	FamilyFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void creatorBecomesAdminAndCanOnlyBeInOneFamily() {
		Session alice = TestUsers.register(this.mvc);

		MvcTestResult created = createFamily(alice, "Smiths");
		assertThat(created).hasStatus(HttpStatus.CREATED);
		assertThat(created).bodyJson().extractingPath("$.name").isEqualTo("Smiths");
		assertThat(created).bodyJson().extractingPath("$.members[0].role").isEqualTo("admin");

		assertProblem(createFamily(alice, "Second"), HttpStatus.CONFLICT, "already-in-family");
	}

	@Test
	void userWithoutFamilyGetsNotFound() {
		Session loner = TestUsers.register(this.mvc);

		assertThat(this.api.get(loner, "/api/v1/families/current")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void invitationLetsTheOwnerOfThatEmailJoin() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = verified(TestUsers.register(this.mvc));
		Session carol = verified(TestUsers.register(this.mvc));
		createFamily(alice, "Smiths");

		MvcTestResult invited = this.api.invite(alice, bob.email().toUpperCase());
		UUID invitationId = TestApi.id(invited);
		assertThat(invited).bodyJson().extractingPath("$.email").isEqualTo(bob.email());
		assertThat(this.api.get(alice, "/api/v1/families/current/invitations")).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(1);

		MvcTestResult received = this.api.get(bob, "/api/v1/invitations");
		assertThat(received).hasStatusOk();
		assertThat(received).bodyJson().extractingPath("$[0].id").isEqualTo(invitationId.toString());
		assertThat(received).bodyJson().extractingPath("$[0].familyName").isEqualTo("Smiths");
		assertThat(received).bodyJson().extractingPath("$[0].memberCount").isEqualTo(1);
		assertThat(this.api.get(carol, "/api/v1/invitations")).bodyJson().extractingPath("$.length()").isEqualTo(0);

		// Only the invited email can use it.
		assertProblem(accept(carol, invitationId), HttpStatus.NOT_FOUND, "invalid-invite");

		MvcTestResult joined = accept(bob, invitationId);
		assertThat(joined).hasStatusOk();
		assertThat(joined).bodyJson().extractingPath("$.members.length()").isEqualTo(2);
		assertThat(joined).bodyJson().extractingPath("$.members[1].email").isEqualTo(bob.email());
		assertThat(joined).bodyJson().extractingPath("$.members[1].role").isEqualTo("member");

		assertProblem(accept(bob, invitationId), HttpStatus.NOT_FOUND, "invalid-invite");
		assertThat(this.api.get(alice, "/api/v1/families/current/invitations")).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(0);
	}

	@Test
	void anEmailCanBeInvitedBeforeItHasAnAccount() {
		Session alice = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");
		String email = TestUsers.uniqueEmail();

		assertThat(this.api.invite(alice, email)).hasStatus(HttpStatus.CREATED);

		Session newcomer = verified(TestUsers.register(this.mvc, email));
		assertThat(this.api.get(newcomer, "/api/v1/invitations")).bodyJson()
			.extractingPath("$[0].familyName")
			.isEqualTo("Smiths");
	}

	@Test
	void invitationsNeedAnEmailVerifiedByGoogle() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");
		UUID invitationId = TestApi.id(this.api.invite(alice, bob.email()));

		assertProblem(this.api.get(bob, "/api/v1/invitations"), HttpStatus.FORBIDDEN, "email-not-verified");
		assertProblem(accept(bob, invitationId), HttpStatus.FORBIDDEN, "email-not-verified");
		assertProblem(decline(bob, invitationId), HttpStatus.FORBIDDEN, "email-not-verified");
	}

	@Test
	void acceptingMovesTheUserOutOfTheirFamilyAndHandsOverAdmin() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");
		this.api.family(bob, carol);

		assertThat(this.api.join(alice, bob)).hasStatusOk();

		assertThat(this.api.get(bob, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Smiths");
		MvcTestResult oldFamily = this.api.get(carol, "/api/v1/families/current");
		assertThat(oldFamily).bodyJson().extractingPath("$.members.length()").isEqualTo(1);
		assertThat(oldFamily).bodyJson().extractingPath("$.members[0].role").isEqualTo("admin");
	}

	@Test
	void acceptingAsTheLastMemberDeletesTheOldFamily() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");
		UUID soloId = TestApi.id(createFamily(bob, "Solo"));

		assertThat(this.api.join(alice, bob)).hasStatusOk();

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM families WHERE id = ?", Integer.class, soloId))
			.isZero();
	}

	@Test
	void duplicateInvitationsAndInvitingMembersAreRejected() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");

		assertThat(this.api.invite(alice, bob.email())).hasStatus(HttpStatus.CREATED);
		assertProblem(this.api.invite(alice, bob.email()), HttpStatus.CONFLICT, "already-invited");

		UUID invitationId = UUID.fromString(TestUsers.json(this.api.get(alice, "/api/v1/families/current/invitations"),
				"$[0].id"));
		this.api.verifyEmail(bob);
		assertThat(accept(bob, invitationId)).hasStatusOk();
		assertProblem(this.api.invite(alice, bob.email()), HttpStatus.CONFLICT, "already-member");
	}

	@Test
	void aFamilyHasAtMostTenSeatsIncludingPendingInvitations() {
		Session alice = TestUsers.register(this.mvc);
		List<Session> members = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			members.add(TestUsers.register(this.mvc));
		}
		this.api.family(alice, members.toArray(Session[]::new));

		// 8 members + 2 invitations = 10 seats.
		UUID ninthSeat = TestApi.id(this.api.invite(alice, TestUsers.uniqueEmail()));
		assertThat(this.api.invite(alice, TestUsers.uniqueEmail())).hasStatus(HttpStatus.CREATED);
		assertProblem(this.api.invite(alice, TestUsers.uniqueEmail()), HttpStatus.CONFLICT, "family-full");

		assertThat(this.api.delete(alice, "/api/v1/families/current/invitations/{id}", ninthSeat))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.invite(alice, TestUsers.uniqueEmail())).hasStatus(HttpStatus.CREATED);
		assertProblem(this.api.delete(alice, "/api/v1/families/current/invitations/{id}", ninthSeat),
				HttpStatus.NOT_FOUND, "not-found");
	}

	@Test
	void declinedAndRevokedInvitationsCanNoLongerBeAccepted() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = verified(TestUsers.register(this.mvc));
		Session carol = verified(TestUsers.register(this.mvc));
		createFamily(alice, "Smiths");
		UUID bobsInvitation = TestApi.id(this.api.invite(alice, bob.email()));
		UUID carolsInvitation = TestApi.id(this.api.invite(alice, carol.email()));

		assertThat(decline(bob, bobsInvitation)).hasStatus(HttpStatus.NO_CONTENT);
		assertProblem(accept(bob, bobsInvitation), HttpStatus.NOT_FOUND, "invalid-invite");

		assertThat(this.api.delete(alice, "/api/v1/families/current/invitations/{id}", carolsInvitation))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(carol, "/api/v1/invitations")).bodyJson().extractingPath("$.length()").isEqualTo(0);
		assertProblem(accept(carol, carolsInvitation), HttpStatus.NOT_FOUND, "invalid-invite");

		// A declined invitation doesn't block a new one.
		assertThat(this.api.invite(alice, bob.email())).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void membersCannotManageTheFamily() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID invitationId = TestApi.id(this.api.invite(alice, TestUsers.uniqueEmail()));

		assertProblem(this.api.invite(bob, TestUsers.uniqueEmail()), HttpStatus.FORBIDDEN, "admin-required");
		assertProblem(this.api.delete(bob, "/api/v1/families/current/invitations/{id}", invitationId),
				HttpStatus.FORBIDDEN, "admin-required");
		assertThat(this.api.delete(bob, "/api/v1/families/current/members/{id}", alice.userId()))
			.hasStatus(HttpStatus.FORBIDDEN);
		// Seeing who's invited is fine for any member.
		assertThat(this.api.get(bob, "/api/v1/families/current/invitations")).hasStatusOk();
	}

	@Test
	void lastAdminMustPromoteSomeoneBeforeLeaving() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);

		assertProblem(this.api.postEmpty(alice, "/api/v1/families/current/leave"), HttpStatus.CONFLICT, "last-admin");

		assertThat(this.mvc.patch()
			.uri("/api/v1/families/current/members/{id}", bob.userId())
			.header("Authorization", alice.bearer())
			.contentType("application/json")
			.content("""
					{"role": "admin"}
					""")).hasStatusOk();
		assertThat(this.api.postEmpty(alice, "/api/v1/families/current/leave")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(bob, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.members.length()")
			.isEqualTo(1);
	}

	@Test
	void lastMemberLeavingDeletesTheFamily() {
		Session alice = TestUsers.register(this.mvc);
		createFamily(alice, "Solo");

		assertThat(this.api.postEmpty(alice, "/api/v1/families/current/leave")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(alice, "/api/v1/families/current")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(createFamily(alice, "Fresh start")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void adminCanRemoveMembersOfTheirOwnFamilyOnly() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		createFamily(stranger, "Joneses");
		this.api.family(alice, bob);

		assertThat(this.api.delete(alice, "/api/v1/families/current/members/{id}", stranger.userId()))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.delete(alice, "/api/v1/families/current/members/{id}", bob.userId()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(bob, "/api/v1/families/current")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private Session verified(Session session) {
		this.api.verifyEmail(session);
		return session;
	}

	private MvcTestResult createFamily(Session session, String name) {
		return this.api.post(session, "/api/v1/families", """
				{"name": "%s"}
				""".formatted(name));
	}

	private MvcTestResult accept(Session session, UUID invitationId) {
		return this.api.postEmpty(session, "/api/v1/invitations/{id}/accept", invitationId);
	}

	private MvcTestResult decline(Session session, UUID invitationId) {
		return this.api.postEmpty(session, "/api/v1/invitations/{id}/decline", invitationId);
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
