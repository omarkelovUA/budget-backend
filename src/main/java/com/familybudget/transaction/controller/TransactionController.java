package com.familybudget.transaction.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.model.PageView;
import com.familybudget.common.web.CurrentUserId;
import com.familybudget.transaction.model.MonthSummaryView;
import com.familybudget.transaction.model.SplitRequest;
import com.familybudget.transaction.model.TransactionRequest;
import com.familybudget.transaction.model.TransactionView;
import com.familybudget.transaction.service.TransactionService;

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

	private final TransactionService transactionService;

	TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@GetMapping
	PageView<TransactionView> list(@CurrentUserId UUID userId, @RequestParam(required = false) @Nullable UUID accountId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
		return this.transactionService.list(userId, accountId, page, size);
	}

	/** Dashboard totals: {@code ?period=YYYY-MM}. */
	@GetMapping("/summary")
	List<MonthSummaryView> summary(@CurrentUserId UUID userId, @RequestParam YearMonth period) {
		return this.transactionService.monthSummary(userId, period);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TransactionView create(@CurrentUserId UUID userId, @Valid @RequestBody TransactionRequest request) {
		return this.transactionService.create(userId, request);
	}

	@PostMapping("/{id}/split")
	@ResponseStatus(HttpStatus.CREATED)
	List<TransactionView> split(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Valid @RequestBody SplitRequest request) {
		return this.transactionService.split(userId, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.transactionService.delete(userId, id);
	}

}
