package com.familybudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.TestUsers.Session;

/** Authenticated API calls and fixtures shared by the feature-module integration tests. */
public final class TestApi {

	public static final String PROBLEMS = "https://familybudget.app/problems/";

	private final MockMvcTester mvc;

	private final JdbcTemplate jdbc;

	public TestApi(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
	}

	public MvcTestResult get(Session session, String uri, Object... vars) {
		return this.mvc.get().uri(uri, vars).header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
	}

	public MvcTestResult post(Session session, String uri, String json) {
		return this.mvc.post()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	public MvcTestResult put(Session session, String uri, String json) {
		return this.mvc.put()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	public MvcTestResult delete(Session session, String uri, Object... vars) {
		return this.mvc.delete().uri(uri, vars).header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
	}

	/** Creates a family owned by {@code admin} and joins every other session to it. */
	public void family(Session admin, Session... members) {
		assertThat(post(admin, "/api/v1/families", """
				{"name": "Family"}
				""")).hasStatus(HttpStatus.CREATED);
		for (Session member : members) {
			assertThat(join(admin, member)).hasStatusOk();
		}
	}

	/** Invites {@code member} into {@code admin}'s family and accepts as {@code member}. */
	public MvcTestResult join(Session admin, Session member) {
		verifyEmail(member);
		UUID invitationId = id(invite(admin, member.email()));
		return postEmpty(member, "/api/v1/invitations/{id}/accept", invitationId);
	}

	public MvcTestResult invite(Session admin, String email) {
		return post(admin, "/api/v1/families/current/invitations", """
				{"email": "%s"}
				""".formatted(email));
	}

	/** A POST without a body, e.g. accepting an invitation. */
	public MvcTestResult postEmpty(Session session, String uri, Object... vars) {
		return this.mvc.post().uri(uri, vars).header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
	}

	/**
	 * Marks the user as Google-linked, which is what makes their email count as verified
	 * (UserService#hasVerifiedEmail). GoogleLoginTests covers the real linking path.
	 */
	public void verifyEmail(Session session) {
		this.jdbc.update("UPDATE users SET google_subject = ? WHERE id = ?", "google-" + session.userId(),
				session.userId());
	}

	public MvcTestResult createAccount(Session owner, String name, String balance, String currency,
			Grant... grants) {
		return post(owner, "/api/v1/accounts", accountJson(name, balance, currency, grants));
	}

	public UUID account(Session owner, String name, String balance, Grant... grants) {
		return id(createAccount(owner, name, balance, "UAH", grants));
	}

	public MvcTestResult updateAccount(Session session, UUID id, String name, String balance, Grant... grants) {
		return put(session, "/api/v1/accounts/" + id, accountJson(name, balance, "UAH", grants));
	}

	public MvcTestResult closeAccount(Session session, UUID id, @Nullable UUID targetAccountId) {
		return post(session, "/api/v1/accounts/" + id + "/close", (targetAccountId != null) ? """
				{"targetAccountId": "%s"}
				""".formatted(targetAccountId) : "{}");
	}

	private static String accountJson(String name, String balance, String currency, Grant... grants) {
		String grantsJson = Arrays.stream(grants)
			.map((grant) -> """
					{"userId": "%s", "level": "%s"}""".formatted(grant.member().userId(), grant.level()))
			.collect(Collectors.joining(", "));
		return """
				{"name": "%s", "balance": %s, "currency": "%s", "icon": "card", "color": "green", "grants": [%s]}
				""".formatted(name, balance, currency, grantsJson);
	}

	public static Grant visible(Session member) {
		return new Grant(member, "visible");
	}

	public static Grant shared(Session member) {
		return new Grant(member, "shared");
	}

	public record Grant(Session member, String level) {
	}

	public MvcTestResult createCategory(Session session, String type, String name, String visibility) {
		return post(session, "/api/v1/categories", """
				{"type": "%s", "name": "%s", "icon": "food", "color": "red", "visibility": "%s"}
				""".formatted(type, name, visibility));
	}

	public UUID category(Session session, String type, String name, String visibility) {
		return id(createCategory(session, type, name, visibility));
	}

	public MvcTestResult pay(Session session, UUID accountId, UUID categoryId, String amount, String date) {
		return post(session, "/api/v1/transactions", """
				{"kind": "payment", "amount": %s, "date": "%s", "accountId": "%s", "categoryId": "%s"}
				""".formatted(amount, date, accountId, categoryId));
	}

	public MvcTestResult transfer(Session session, UUID fromAccountId, UUID toAccountId, String amount) {
		return post(session, "/api/v1/transactions", """
				{"kind": "transfer", "amount": %s, "date": "2026-10-05", "fromAccountId": "%s", "toAccountId": "%s"}
				""".formatted(amount, fromAccountId, toAccountId));
	}

	public BigDecimal balance(Session session, UUID accountId) {
		MvcTestResult result = get(session, "/api/v1/accounts/{id}", accountId);
		assertThat(result).hasStatusOk();
		return new BigDecimal(TestUsers.json(result, "$.balance"));
	}

	public static UUID id(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(TestUsers.json(result, "$.id"));
	}

}
