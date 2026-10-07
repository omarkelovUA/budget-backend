package com.familybudget.family;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.familybudget.common.CurrentUserId;

@RestController
@RequestMapping("/api/v1/families")
class FamilyController {

	private final FamilyService familyService;

	FamilyController(FamilyService familyService) {
		this.familyService = familyService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	FamilyView create(@CurrentUserId UUID userId, @Valid @RequestBody CreateFamilyRequest request) {
		return this.familyService.create(userId, request.name());
	}

	@GetMapping("/current")
	FamilyView current(@CurrentUserId UUID userId) {
		return this.familyService.current(userId);
	}

	@PostMapping("/current/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	InvitationView invite(@CurrentUserId UUID userId, @Valid @RequestBody InviteRequest request) {
		return this.familyService.invite(userId, request.email());
	}

	@GetMapping("/current/invitations")
	List<InvitationView> invitations(@CurrentUserId UUID userId) {
		return this.familyService.sentInvitations(userId);
	}

	@DeleteMapping("/current/invitations/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revokeInvitation(@CurrentUserId UUID userId, @PathVariable UUID invitationId) {
		this.familyService.revokeInvitation(userId, invitationId);
	}

	@PostMapping("/current/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void leave(@CurrentUserId UUID userId) {
		this.familyService.leave(userId);
	}

	@DeleteMapping("/current/members/{memberUserId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeMember(@CurrentUserId UUID userId, @PathVariable UUID memberUserId) {
		this.familyService.removeMember(userId, memberUserId);
	}

	@PatchMapping("/current/members/{memberUserId}")
	FamilyView changeRole(@CurrentUserId UUID userId, @PathVariable UUID memberUserId,
			@Valid @RequestBody ChangeRoleRequest request) {
		return this.familyService.changeRole(userId, memberUserId, request.role());
	}

	record CreateFamilyRequest(@NotBlank @Size(max = 100) String name) {
	}

	record InviteRequest(@NotBlank @Email @Size(max = 320) String email) {
	}

	record ChangeRoleRequest(@NotNull FamilyRole role) {
	}

}
