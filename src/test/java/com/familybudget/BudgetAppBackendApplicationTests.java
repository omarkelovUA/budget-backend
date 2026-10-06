package com.familybudget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * M6 exit criteria: the app boots against a real Postgres (Testcontainers), Flyway runs,
 * the health endpoint is public and everything else requires a JWT.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BudgetAppBackendApplicationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void healthEndpointIsPublicAndUp() {
		assertThat(this.mvc.get().uri("/actuator/health")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
	}

	@Test
	void apiRequiresAuthentication() {
		assertThat(this.mvc.get().uri("/api/v1/accounts")).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo(401);
	}

}
