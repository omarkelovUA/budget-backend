package com.familybudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.familybudget.TestUsers.Session;

/** Authenticated API calls and fixtures shared by the feature-module integration tests. */
public final class TestApi {

	public static final String PROBLEMS = "https://familybudget.app/problems/";

	private final MockMvcTester mvc;

	public TestApi(MockMvcTester mvc) {
		this.mvc = mvc;
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

	public MvcTestResult delete(Session session, String uri) {
		return this.mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
	}

	/** Creates a family owned by {@code admin} and joins every other session to it. */
	public void family(Session admin, Session... members) {
		assertThat(post(admin, "/api/v1/families", """
				{"name": "Family"}
				""")).hasStatus(HttpStatus.CREATED);
		for (Session member : members) {
			MvcTestResult invite = post(admin, "/api/v1/families/current/invites", "");
			assertThat(post(member, "/api/v1/families/join", """
					{"code": "%s"}
					""".formatted(TestUsers.json(invite, "$.code")))).hasStatusOk();
		}
	}

	public MvcTestResult createAccount(Session session, String name, String balance, String currency,
			String visibility) {
		return post(session, "/api/v1/accounts", """
				{"name": "%s", "balance": %s, "currency": "%s", "icon": "card", "color": "green", "visibility": "%s"}
				""".formatted(name, balance, currency, visibility));
	}

	public UUID account(Session session, String name, String balance, String visibility) {
		return id(createAccount(session, name, balance, "UAH", visibility));
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
