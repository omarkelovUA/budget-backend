package com.familybudget.family;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.IntegrationTest;
import com.familybudget.TestUsers;
import com.familybudget.TestUsers.Session;

/** Family creation, invite/join and admin-only management (ARCHITECTURE.md §3.2, §6.3). */
@IntegrationTest
class FamilyFlowTests {

	private static final String PROBLEMS = "https://familybudget.app/problems/";

	@Autowired
	private MockMvcTester mvc;

	@Test
	void creatorBecomesAdminAndCanOnlyBeInOneFamily() {
		Session alice = TestUsers.register(this.mvc);

		MvcTestResult created = createFamily(alice, "Smiths");
		assertThat(created).hasStatus(HttpStatus.CREATED);
		assertThat(created).bodyJson().extractingPath("$.name").isEqualTo("Smiths");
		assertThat(created).bodyJson().extractingPath("$.members[0].role").isEqualTo("ADMIN");

		assertThat(createFamily(alice, "Second")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(PROBLEMS + "already-in-family");
	}

	@Test
	void userWithoutFamilyGetsNotFound() {
		Session loner = TestUsers.register(this.mvc);

		assertThat(get(loner, "/api/v1/families/current")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void inviteCodeLetsOneUserJoin() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session carol = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");

		String code = invite(alice);
		MvcTestResult joined = join(bob, code.toLowerCase());
		assertThat(joined).hasStatusOk();
		assertThat(joined).bodyJson().extractingPath("$.members.length()").isEqualTo(2);
		assertThat(joined).bodyJson().extractingPath("$.members[1].email").isEqualTo(bob.email());
		assertThat(joined).bodyJson().extractingPath("$.members[1].role").isEqualTo("MEMBER");

		assertThat(join(carol, code)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(PROBLEMS + "invalid-invite");
		assertThat(get(bob, "/api/v1/families/current")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Smiths");
	}

	@Test
	void membersCannotManageTheFamily() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");
		join(bob, invite(alice));

		assertThat(post(bob, "/api/v1/families/current/invites")).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(PROBLEMS + "admin-required");
		assertThat(this.mvc.delete()
			.uri("/api/v1/families/current/members/{id}", alice.userId())
			.header(HttpHeaders.AUTHORIZATION, bob.bearer())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void lastAdminMustPromoteSomeoneBeforeLeaving() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");
		join(bob, invite(alice));

		assertThat(post(alice, "/api/v1/families/current/leave")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo(PROBLEMS + "last-admin");

		assertThat(this.mvc.patch()
			.uri("/api/v1/families/current/members/{id}", bob.userId())
			.header(HttpHeaders.AUTHORIZATION, alice.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"role": "ADMIN"}
					""")).hasStatusOk();
		assertThat(post(alice, "/api/v1/families/current/leave")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(get(bob, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.members.length()")
			.isEqualTo(1);
	}

	@Test
	void lastMemberLeavingDeletesTheFamily() {
		Session alice = TestUsers.register(this.mvc);
		createFamily(alice, "Solo");

		assertThat(post(alice, "/api/v1/families/current/leave")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(get(alice, "/api/v1/families/current")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(createFamily(alice, "Fresh start")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void adminCanRemoveMembersOfTheirOwnFamilyOnly() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		Session stranger = TestUsers.register(this.mvc);
		createFamily(alice, "Smiths");
		createFamily(stranger, "Joneses");
		join(bob, invite(alice));

		assertThat(this.mvc.delete()
			.uri("/api/v1/families/current/members/{id}", stranger.userId())
			.header(HttpHeaders.AUTHORIZATION, alice.bearer())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.delete()
			.uri("/api/v1/families/current/members/{id}", bob.userId())
			.header(HttpHeaders.AUTHORIZATION, alice.bearer())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(get(bob, "/api/v1/families/current")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult createFamily(Session session, String name) {
		return this.mvc.post()
			.uri("/api/v1/families")
			.header(HttpHeaders.AUTHORIZATION, session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "%s"}
					""".formatted(name))
			.exchange();
	}

	private String invite(Session admin) {
		MvcTestResult result = post(admin, "/api/v1/families/current/invites");
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return TestUsers.json(result, "$.code");
	}

	private MvcTestResult join(Session session, String code) {
		return this.mvc.post()
			.uri("/api/v1/families/join")
			.header(HttpHeaders.AUTHORIZATION, session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"code": "%s"}
					""".formatted(code))
			.exchange();
	}

	private MvcTestResult get(Session session, String uri) {
		return this.mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
	}

	private MvcTestResult post(Session session, String uri) {
		return this.mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
	}

}
