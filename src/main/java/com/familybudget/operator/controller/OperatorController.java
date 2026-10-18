package com.familybudget.operator.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.web.CurrentUserId;
import com.familybudget.operator.model.GrantView;
import com.familybudget.operator.model.OperatorStatsView;
import com.familybudget.operator.service.OperatorService;

@RestController
@RequestMapping("/api/v1/operator")
class OperatorController {

	private final OperatorService operatorService;

	OperatorController(OperatorService operatorService) {
		this.operatorService = operatorService;
	}

	@GetMapping("/stats")
	OperatorStatsView stats(@CurrentUserId UUID userId) {
		return this.operatorService.stats(userId);
	}

	@GetMapping("/grants")
	List<GrantView> grants(@CurrentUserId UUID userId) {
		return this.operatorService.grants(userId);
	}

	@PostMapping("/grants")
	GrantView grant(@CurrentUserId UUID userId, @Valid @RequestBody GrantRequest request) {
		return this.operatorService.grant(userId, request.email(), request.expiresAt());
	}

	@DeleteMapping("/grants/{grantee}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke(@CurrentUserId UUID userId, @PathVariable UUID grantee) {
		this.operatorService.revoke(userId, grantee);
	}

	record GrantRequest(@NotBlank @Email @Size(max = 320) String email, @Nullable Instant expiresAt) {
	}

}
