package com.familybudget;

import org.springframework.boot.SpringApplication;

public class TestBudgetAppBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BudgetAppBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
