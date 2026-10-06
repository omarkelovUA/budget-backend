package com.familybudget;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces the package-by-feature boundaries from ARCHITECTURE.md §5.2: modules may only
 * use each other's public API, never internals (e.g. another feature's repository).
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(BudgetAppBackendApplication.class);

	@Test
	void verifiesModuleStructure() {
		this.modules.verify();
	}

	@Test
	void writesModuleDocumentation() {
		new Documenter(this.modules).writeDocumentation();
	}

}
