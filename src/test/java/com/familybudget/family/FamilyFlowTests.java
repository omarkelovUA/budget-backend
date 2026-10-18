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

/** Own families, email invitations and admin-only management (ARCHITECTURE.md §3.2, §6.3). */
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
	void signUpCreatesAFamilyOfOneNamedInTheUsersLanguage() {
		Session alice = TestUsers.register(this.mvc);
		Session olena = TestUsers.registerIn(this.mvc, "uk-UA");

		MvcTestResult own = this.api.get(alice, "/api/v1/families/current");
		assertThat(own).hasStatusOk();
		assertThat(own).bodyJson().extractingPath("$.name").isEqualTo("Family");
		assertThat(own).bodyJson().extractingPath("$.members.length()").isEqualTo(1);
		assertThat(own).bodyJson().extractingPath("$.members[0].role").isEqualTo("admin");
		assertThat(this.api.get(olena, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Родина");
		// Families can't be created any more: everyone already has one.
		assertThat(this.api.post(alice, "/api/v1/families", """
				{"name": "Second"}
				""")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void onlyAdminsRenameTheFamily() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);

		MvcTestResult renamed = rename(alice, "  Smiths ");
		assertThat(renamed).hasStatusOk();
		assertThat(renamed).bodyJson().extractingPath("$.name").isEqualTo("Smiths");
		assertProblem(rename(bob, "Bobs"), HttpStatus.FORBIDDEN, "admin-required");
		assertThat(rename(alice, " ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.api.get(bob, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Smiths");
	}

	@Test
	void invitationLetsTheOwnerOfThatEmailJoin() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		rename(alice, "Smiths");

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
		rename(alice, "Smiths");
		String email = TestUsers.uniqueEmail();

		assertThat(this.api.invite(alice, email)).hasStatus(HttpStatus.CREATED);

		Session newcomer = TestUsers.register(this.mvc, email);
		assertThat(this.api.get(newcomer, "/api/v1/invitations")).bodyJson()
			.extractingPath("$[0].familyName")
			.isEqualTo("Smiths");
	}

	@Test
	void invitationsNeedAnEmailVerifiedByGoogle() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = this.api.unlinkGoogle(TestUsers.register(this.mvc));
		rename(alice, "Smiths");
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
		rename(alice, "Smiths");
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
		rename(alice, "Smiths");
		UUID soloId = this.api.familyId(bob);

		assertThat(this.api.join(alice, bob)).hasStatusOk();

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM families WHERE id = ?", Integer.class, soloId))
			.isZero();
	}

	@Test
	void duplicateInvitationsAndInvitingMembersAreRejected() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		rename(alice, "Smiths");

		assertThat(this.api.invite(alice, bob.email())).hasStatus(HttpStatus.CREATED);
		assertProblem(this.api.invite(alice, bob.email()), HttpStatus.CONFLICT, "already-invited");

		UUID invitationId = UUID.fromString(TestUsers.json(this.api.get(alice, "/api/v1/families/current/invitations"),
				"$[0].id"));
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
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		rename(alice, "Smiths");
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
	void leavingGivesTheUserAFamilyOfTheirOwn() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		rename(alice, "Smiths");

		MvcTestResult left = this.api.postEmpty(bob, "/api/v1/families/current/leave");
		assertThat(left).hasStatusOk();
		assertThat(left).bodyJson().extractingPath("$.name").isEqualTo("Family");
		assertThat(left).bodyJson().extractingPath("$.members.length()").isEqualTo(1);
		assertThat(left).bodyJson().extractingPath("$.members[0].role").isEqualTo("admin");
		assertThat(this.api.get(alice, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.members.length()")
			.isEqualTo(1);

		// Alone, there's nothing to leave.
		assertProblem(this.api.postEmpty(bob, "/api/v1/families/current/leave"), HttpStatus.CONFLICT,
				"alone-in-family");
	}

	@Test
	void theOnlyAdminLeavingHandsOverToTheLongestStandingMember() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		this.api.family(alice, bob, carol);

		assertThat(this.api.postEmpty(alice, "/api/v1/families/current/leave")).hasStatusOk();

		MvcTestResult family = this.api.get(bob, "/api/v1/families/current");
		assertThat(family).bodyJson().extractingPath("$.members[0].userId").isEqualTo(bob.userId().toString());
		assertThat(family).bodyJson().extractingPath("$.members[0].role").isEqualTo("admin");
		assertThat(family).bodyJson().extractingPath("$.members[1].role").isEqualTo("member");
		// Demoting the last admin is still refused.
		assertProblem(this.mvc.patch()
			.uri("/api/v1/families/current/members/{id}", bob.userId())
			.header("Authorization", bob.bearer())
			.contentType("application/json")
			.content("""
					{"role": "member"}
					""")
			.exchange(), HttpStatus.CONFLICT, "last-admin");
	}

	@Test
	void theLastMemberMovingOutKeepsTheFamilysCategoriesAndBudgetsAsPrivate() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		this.api.clearDefaults(alice, bob, carol);
		UUID food = this.api.category(alice, "expense", "Food", "shared");
		UUID card = this.api.account(alice, "Card", "100");
		this.api.pay(alice, card, food, "30", "2026-10-01");
		assertThat(this.api.post(alice, "/api/v1/budgets", """
				{"categoryId": "%s", "period": "2026-10", "plannedAmount": 100, "visibility": "shared"}
				""".formatted(food))).hasStatus(HttpStatus.CREATED);

		// Bob leaving doesn't delete the family, so nothing changes hands yet.
		assertThat(this.api.postEmpty(bob, "/api/v1/families/current/leave")).hasStatusOk();
		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson()
			.extractingPath("$[0].visibility")
			.isEqualTo("shared");

		assertThat(this.api.join(carol, alice)).hasStatusOk();

		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson()
			.extractingPath("$[0].visibility")
			.isEqualTo("private");
		MvcTestResult budgets = this.api.get(alice, "/api/v1/budgets");
		assertThat(budgets).bodyJson().extractingPath("$[0].visibility").isEqualTo("private");
		assertThat(budgets).bodyJson().extractingPath("$[0].factAmount").isEqualTo(30.0);
		assertThat(this.api.get(bob, "/api/v1/categories")).bodyJson().extractingPath("$.length()").isEqualTo(0);
	}

	@Test
	void movingOutAsTheLastMemberKeepsTheOldFamilysCategories() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.clearDefaults(alice, bob);
		this.api.category(bob, "expense", "Hobby", "shared");

		assertThat(this.api.join(alice, bob)).hasStatusOk();

		MvcTestResult bobsCategories = this.api.get(bob, "/api/v1/categories");
		assertThat(bobsCategories).bodyJson().extractingPath("$[0].name").isEqualTo("Hobby");
		assertThat(bobsCategories).bodyJson().extractingPath("$[0].visibility").isEqualTo("private");
		// It stays Bob's: his new family doesn't get it.
		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson().extractingPath("$.length()").isEqualTo(0);
	}

	@Test
	void adminCanRemoveMembersOfTheirOwnFamilyOnly() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		this.api.family(alice, bob);

		assertThat(this.api.delete(alice, "/api/v1/families/current/members/{id}", stranger.userId()))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.api.delete(alice, "/api/v1/families/current/members/{id}", bob.userId()))
			.hasStatus(HttpStatus.NO_CONTENT);
		// The removed member gets a family of their own.
		MvcTestResult bobsFamily = this.api.get(bob, "/api/v1/families/current");
		assertThat(bobsFamily).bodyJson().extractingPath("$.members.length()").isEqualTo(1);
		assertThat(bobsFamily).bodyJson().extractingPath("$.members[0].role").isEqualTo("admin");
	}

	private MvcTestResult rename(Session session, String name) {
		return this.mvc.patch()
			.uri("/api/v1/families/current")
			.header("Authorization", session.bearer())
			.contentType("application/json")
			.content("""
					{"name": "%s"}
					""".formatted(name))
			.exchange();
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
