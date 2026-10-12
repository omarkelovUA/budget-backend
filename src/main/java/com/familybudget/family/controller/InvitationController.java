package com.familybudget.family.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.web.CurrentUserId;
import com.familybudget.family.model.FamilyView;
import com.familybudget.family.model.ReceivedInvitationView;
import com.familybudget.family.service.FamilyService;

/** Invitations addressed to the caller's email (the family side is in {@link FamilyController}). */
@RestController
@RequestMapping("/api/v1/invitations")
class InvitationController {

	private final FamilyService familyService;

	InvitationController(FamilyService familyService) {
		this.familyService = familyService;
	}

	@GetMapping
	List<ReceivedInvitationView> received(@CurrentUserId UUID userId) {
		return this.familyService.receivedInvitations(userId);
	}

	@PostMapping("/{invitationId}/accept")
	FamilyView accept(@CurrentUserId UUID userId, @PathVariable UUID invitationId) {
		return this.familyService.accept(userId, invitationId);
	}

	@PostMapping("/{invitationId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void decline(@CurrentUserId UUID userId, @PathVariable UUID invitationId) {
		this.familyService.decline(userId, invitationId);
	}

}
