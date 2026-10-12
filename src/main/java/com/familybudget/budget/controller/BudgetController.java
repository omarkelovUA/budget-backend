package com.familybudget.budget.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.budget.model.BudgetRequest;
import com.familybudget.budget.model.BudgetView;
import com.familybudget.budget.service.BudgetService;
import com.familybudget.common.web.CurrentUserId;

@RestController
@RequestMapping("/api/v1/budgets")
class BudgetController {

	private final BudgetService budgetService;

	BudgetController(BudgetService budgetService) {
		this.budgetService = budgetService;
	}

	/** {@code ?period=YYYY-MM} narrows the list to one month. */
	@GetMapping
	List<BudgetView> list(@CurrentUserId UUID userId, @RequestParam(required = false) @Nullable YearMonth period) {
		return this.budgetService.list(userId, period);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BudgetView create(@CurrentUserId UUID userId, @Valid @RequestBody BudgetRequest request) {
		return this.budgetService.create(userId, request);
	}

	@PutMapping("/{id}")
	BudgetView update(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody BudgetRequest request) {
		return this.budgetService.update(userId, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.budgetService.delete(userId, id);
	}

}
