package com.familybudget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.CurrentUserId;

@RestController
@RequestMapping("/api/v1/family-transfers")
class FamilyTransferController {

	private final FamilyTransferService familyTransfers;

	FamilyTransferController(FamilyTransferService familyTransfers) {
		this.familyTransfers = familyTransfers;
	}

	@GetMapping
	FamilyTransfersView inTransit(@CurrentUserId UUID userId) {
		return this.familyTransfers.inTransit(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TransactionView send(@CurrentUserId UUID userId, @Valid @RequestBody SendRequest request) {
		return this.familyTransfers.send(userId, request.fromAccountId(), request.recipientId(), request.amount(),
				request.date(), request.note());
	}

	@PostMapping("/{id}/collect")
	@ResponseStatus(HttpStatus.CREATED)
	TransactionView collect(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Valid @RequestBody WithdrawRequest request) {
		return this.familyTransfers.collect(userId, id, request.toAccountId(), request.amount());
	}

	@PostMapping("/{id}/take-back")
	@ResponseStatus(HttpStatus.CREATED)
	TransactionView takeBack(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Valid @RequestBody WithdrawRequest request) {
		return this.familyTransfers.takeBack(userId, id, request.toAccountId(), request.amount());
	}

	record SendRequest(@NotNull UUID fromAccountId, @NotNull UUID recipientId,
			@NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
			@Nullable LocalDate date, @Nullable @Size(max = 500) String note) {
	}

	/** {@code amount} defaults to everything waiting. */
	record WithdrawRequest(@NotNull UUID toAccountId,
			@Nullable @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {
	}

}
