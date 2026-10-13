package com.familybudget.category;

import static com.familybudget.TestApi.PROBLEMS;
import static com.familybudget.TestApi.shared;
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

/**
 * Family categories (ARCHITECTURE §3.2): shared by default, managed by the admin, added by
 * anyone, never duplicated for a member, and hidden only while nobody else uses them.
 */
@IntegrationTest
class FamilyCategoryFlowTests {

	private final MockMvcTester mvc;

	private final TestApi api;

	@Autowired
	FamilyCategoryFlowTests(MockMvcTester mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.api = new TestApi(mvc, jdbc);
	}

	@Test
	void joiningMergesTheNewcomersCategoriesIntoTheFamilysOnes() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		UUID bobsCard = this.api.account(bob, "Bob card", "100");
		UUID bobsGroceries = id(bob, "Groceries");
		this.api.category(bob, "expense", "Pets", "private");
		this.api.pay(bob, bobsCard, bobsGroceries, "30", "2026-10-01");

		this.api.family(alice, bob);

		// Bob sees one set: the family's eight defaults plus his own Pets.
		MvcTestResult bobsCategories = this.api.get(bob, "/api/v1/categories");
		assertThat(bobsCategories).bodyJson().extractingPath("$.length()").isEqualTo(9);
		assertThat(bobsCategories).bodyJson()
			.extractingPath("$[?(@.name == 'Pets')].visibility")
			.asArray()
			.containsExactly("private");
		UUID familyGroceries = id(alice, "Groceries");
		assertThat(ids(bob)).doesNotContain(bobsGroceries.toString());
		// His payment moved to the family's Groceries.
		assertThat(this.api.get(bob, "/api/v1/transactions")).bodyJson()
			.extractingPath("$.content[0].category.id")
			.isEqualTo(familyGroceries.toString());
	}

	@Test
	void onlyTheAdminChangesOrDeletesAFamilyCategoryButEveryoneUsesIt() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID health = id(alice, "Health");
		UUID bobsCard = this.api.account(bob, "Bob card", "100");

		assertProblem(update(bob, health, "Doctors"), HttpStatus.FORBIDDEN, "admin-required");
		assertProblem(this.api.delete(bob, "/api/v1/categories/{id}", health), HttpStatus.FORBIDDEN,
				"admin-required");
		assertThat(this.api.pay(bob, bobsCard, health, "20", "2026-10-02")).hasStatus(HttpStatus.CREATED);

		assertThat(update(alice, health, "Doctors")).hasStatusOk();
		assertThat(this.api.get(bob, "/api/v1/categories")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.contains("Doctors")
			.doesNotContain("Health");
	}

	@Test
	void aMembersFamilyCategoryIsLiveAndTheAdminIsTold() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);

		UUID garden = this.api.category(bob, "expense", "Garden", "shared");
		UUID books = this.api.category(bob, "expense", "Books", "private");
		assertThat(this.api.postEmpty(bob, "/api/v1/categories/{id}/share", books)).hasStatusOk();

		assertThat(this.api.get(alice, "/api/v1/categories")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.contains("Garden", "Books");
		MvcTestResult added = this.api.get(alice, "/api/v1/categories/added-by-members");
		assertThat(added).bodyJson()
			.extractingPath("$[*].category.id")
			.asArray()
			.containsExactlyInAnyOrder(garden.toString(), books.toString());
		assertThat(added).bodyJson().extractingPath("$[0].addedBy").isEqualTo("Test User");
		assertProblem(this.api.get(bob, "/api/v1/categories/added-by-members"), HttpStatus.FORBIDDEN,
				"admin-required");

		assertThat(this.api.postEmpty(alice, "/api/v1/categories/added-by-members/seen"))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(alice, "/api/v1/categories/added-by-members")).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(0);
		// The admin's own family categories never need telling about.
		this.api.category(alice, "expense", "Sports", "shared");
		assertThat(this.api.get(alice, "/api/v1/categories/added-by-members")).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(0);
	}

	@Test
	void aUserNeverSeesTwoCategoriesWithTheSameTypeAndName() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);

		assertProblem(this.api.createCategory(bob, "expense", "groceries", "private"), HttpStatus.CONFLICT,
				"category-exists");
		// The same name with the other type is a different category.
		assertThat(this.api.createCategory(bob, "income", "Groceries", "private")).hasStatus(HttpStatus.CREATED);

		// Sharing a category merges other members' namesakes into it.
		UUID bobsGarden = this.api.category(bob, "expense", "Garden", "private");
		UUID alicesGarden = this.api.category(alice, "expense", "garden", "private");
		assertThat(this.api.postEmpty(alice, "/api/v1/categories/{id}/share", alicesGarden)).hasStatusOk();
		assertThat(ids(bob)).doesNotContain(bobsGarden.toString()).contains(alicesGarden.toString());

		// Renaming a family category into a member's personal one merges that one in.
		UUID bobsHobby = this.api.category(bob, "expense", "Hobby", "private");
		assertThat(update(alice, id(alice, "Entertainment"), "Hobby")).hasStatusOk();
		assertThat(ids(bob)).doesNotContain(bobsHobby.toString());
		assertThat(this.api.get(bob, "/api/v1/categories")).bodyJson()
			.extractingPath("$[?(@.name == 'Hobby')].visibility")
			.asArray()
			.containsExactly("shared");
	}

	@Test
	void hidingIsRefusedWhileOthersUseTheCategory() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID transport = id(alice, "Transport");
		UUID health = id(alice, "Health");
		UUID entertainment = id(alice, "Entertainment");
		UUID bobsCard = this.api.account(bob, "Bob card", "100");
		this.api.pay(bob, bobsCard, transport, "10", "2026-10-03");
		assertThat(this.api.post(alice, "/api/v1/budgets", """
				{"categoryId": "%s", "period": "2026-10", "plannedAmount": 100, "visibility": "shared"}
				""".formatted(health))).hasStatus(HttpStatus.CREATED);

		assertProblem(hide(bob, entertainment), HttpStatus.FORBIDDEN, "admin-required");
		assertProblem(hide(alice, transport), HttpStatus.CONFLICT, "category-used-by-family");
		assertProblem(hide(alice, health), HttpStatus.CONFLICT, "category-used-by-family");

		MvcTestResult hidden = hide(alice, entertainment);
		assertThat(hidden).hasStatusOk();
		assertThat(hidden).bodyJson().extractingPath("$.visibility").isEqualTo("private");
		assertThat(this.api.get(bob, "/api/v1/categories")).bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.doesNotContain("Entertainment");
	}

	@Test
	void someoneElsesPersonalCategoryShowsAsUnknown() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		this.api.family(alice, bob);
		UUID joint = this.api.account(alice, "Joint", "500", shared(bob));
		UUID bobsHobby = this.api.category(bob, "expense", "Bob's hobby", "private");
		this.api.pay(bob, joint, bobsHobby, "15", "2026-10-04");

		MvcTestResult alicesView = this.api.get(alice, "/api/v1/transactions");
		assertThat(alicesView).bodyJson().extractingPath("$.content[0].category.unknown").isEqualTo(true);
		assertThat(alicesView).bodyJson().extractingPath("$.content[0].category.name").isEqualTo("");
		assertThat(alicesView).bodyJson().extractingPath("$.content[0].category.type").isEqualTo("expense");
		assertThat(this.api.get(bob, "/api/v1/transactions")).bodyJson()
			.extractingPath("$.content[0].category.name")
			.isEqualTo("Bob's hobby");
	}

	@Test
	void theAdminReviewsTheFamilysCategoriesOnce() {
		Session alice = TestUsers.register(this.mvc);
		Session bob = TestUsers.register(this.mvc);
		assertThat(this.api.get(alice, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.categoriesReviewed")
			.isEqualTo(false);
		this.api.family(alice, bob);

		assertProblem(this.api.postEmpty(bob, "/api/v1/families/current/categories-reviewed"), HttpStatus.FORBIDDEN,
				"admin-required");
		assertThat(this.api.postEmpty(alice, "/api/v1/families/current/categories-reviewed"))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.api.get(bob, "/api/v1/families/current")).bodyJson()
			.extractingPath("$.categoriesReviewed")
			.isEqualTo(true);
	}

	private String ids(Session session) {
		return TestUsers.json(this.api.get(session, "/api/v1/categories"), "$[*].id");
	}

	private UUID id(Session session, String name) {
		return UUID.fromString(TestUsers.json(this.api.get(session, "/api/v1/categories"),
				"$[?(@.name == '" + name + "')].id").replaceAll("[\\[\\]\"]", ""));
	}

	private MvcTestResult update(Session session, UUID id, String name) {
		MvcTestResult current = this.api.get(session, "/api/v1/categories");
		String type = TestUsers.json(current, "$[?(@.id == '" + id + "')].type").replaceAll("[\\[\\]\"]", "");
		String visibility = TestUsers.json(current, "$[?(@.id == '" + id + "')].visibility")
			.replaceAll("[\\[\\]\"]", "");
		return this.api.put(session, "/api/v1/categories/" + id, """
				{"type": "%s", "name": "%s", "icon": "assets/home.svg", "color": "red", "visibility": "%s"}
				""".formatted(type, name, visibility));
	}

	private MvcTestResult hide(Session session, UUID id) {
		return this.api.postEmpty(session, "/api/v1/categories/{id}/hide", id);
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
		assertThat(result).hasStatus(status).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + type);
	}

}
