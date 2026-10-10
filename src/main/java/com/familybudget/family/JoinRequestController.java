package com.familybudget.family;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.CurrentUserId;

/**
 * The caller's own request to join another family (the family side, where admins answer
 * it, is in {@link FamilyController}).
 */
@RestController
@RequestMapping("/api/v1/join-requests")
class JoinRequestController {

	private final FamilyService familyService;

	JoinRequestController(FamilyService familyService) {
		this.familyService = familyService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	JoinRequestView create(@CurrentUserId UUID userId, @Valid @RequestBody JoinRequest request) {
		return this.familyService.requestToJoin(userId, request.email());
	}

	/** 204 when the caller has no open request. */
	@GetMapping("/mine")
	ResponseEntity<JoinRequestView> mine(@CurrentUserId UUID userId) {
		return this.familyService.ownJoinRequest(userId)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@DeleteMapping("/mine")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@CurrentUserId UUID userId) {
		this.familyService.cancelJoinRequest(userId);
	}

	record JoinRequest(@NotBlank @Email @Size(max = 320) String email) {
	}

}
