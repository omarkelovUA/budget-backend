package com.familybudget.transaction;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.account.AccountView;
import com.familybudget.common.CurrentUserId;

/**
 * Lives here, not in the account module: closing records a transfer of the remaining
 * balance, and the account module must not depend on transactions.
 */
@RestController
class AccountClosingController {

	private final TransactionService transactionService;

	AccountClosingController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@PostMapping("/api/v1/accounts/{id}/close")
	AccountView close(@CurrentUserId UUID userId, @PathVariable UUID id,
			@RequestBody(required = false) @Nullable CloseAccountRequest request) {
		return this.transactionService.closeAccount(userId, id, (request != null) ? request.targetAccountId() : null);
	}

	/** {@code targetAccountId} is only needed when the balance isn't zero. */
	record CloseAccountRequest(@Nullable UUID targetAccountId) {
	}

}
