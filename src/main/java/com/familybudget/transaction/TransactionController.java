package com.familybudget.transaction;

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

import com.familybudget.common.CurrentUserId;
import com.familybudget.common.PageView;

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

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.transactionService.delete(userId, id);
	}

}
