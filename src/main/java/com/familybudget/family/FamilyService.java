package com.familybudget.family;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.AccessScope;
import com.familybudget.common.ApiException;
import com.familybudget.common.NotFoundException;
import com.familybudget.user.UserService;
import com.familybudget.user.UserView;

/**
 * Public API of the family module. Family-management actions (invite, remove member,
 * change role) require {@link FamilyRole#ADMIN} (ARCHITECTURE.md §6.3).
 */
@Service
@Transactional(readOnly = true)
public class FamilyService {

	/** Members plus open invitations: an invitation reserves a seat until it's answered. */
	static final int MAX_MEMBERS = 10;

	private final FamilyRepository families;

	private final FamilyMemberRepository members;

	private final FamilyInvitationRepository invitations;

	private final UserService userService;

	private final Duration inviteTtl;

	FamilyService(FamilyRepository families, FamilyMemberRepository members, FamilyInvitationRepository invitations,
			UserService userService, @Value("${app.family.invite-ttl}") Duration inviteTtl) {
		this.families = families;
		this.members = members;
		this.invitations = invitations;
		this.userService = userService;
		this.inviteTtl = inviteTtl;
	}

	/** The caller's family, used by later modules to scope shared data (ARCHITECTURE.md §6.3). */
	public Optional<UUID> currentFamilyId(UUID userId) {
		return this.members.findById(userId).map(FamilyMemberEntity::getFamilyId);
	}

	public AccessScope scopeFor(UUID userId) {
		return new AccessScope(userId, currentFamilyId(userId).orElse(null));
	}

	public FamilyView current(UUID userId) {
		return view(membership(userId).getFamilyId());
	}

	@Transactional
	public FamilyView create(UUID userId, String name) {
		requireNoFamily(userId);
		FamilyEntity family = this.families.save(new FamilyEntity(name.strip(), userId));
		this.members.save(new FamilyMemberEntity(userId, family.getId(), FamilyRole.ADMIN));
		return view(family.getId());
	}

	@Transactional
	public InvitationView invite(UUID userId, String email) {
		UUID familyId = requireAdmin(userId).getFamilyId();
		lockFamily(familyId);
		String normalizedEmail = UserService.normalizeEmail(email);
		boolean alreadyMember = this.userService.findByEmail(normalizedEmail)
			.flatMap((user) -> this.members.findById(user.id()))
			.filter((member) -> member.getFamilyId().equals(familyId))
			.isPresent();
		if (alreadyMember) {
			throw new ApiException(HttpStatus.CONFLICT, "already-member", "This person is already in your family");
		}
		Instant now = Instant.now();
		Optional<FamilyInvitationEntity> previous = this.invitations.findByFamilyIdAndEmailAndStatus(familyId,
				normalizedEmail, InvitationStatus.PENDING);
		if (previous.isPresent()) {
			if (previous.get().isOpen(now)) {
				throw new ApiException(HttpStatus.CONFLICT, "already-invited", "This email already has an invitation");
			}
			previous.get().close(InvitationStatus.EXPIRED, now);
			// Free the (family, email) slot of the partial unique index before inserting.
			this.invitations.flush();
		}
		if (seatsTaken(familyId, now) >= MAX_MEMBERS) {
			throw familyFull();
		}
		FamilyInvitationEntity invitation = this.invitations
			.save(new FamilyInvitationEntity(familyId, normalizedEmail, userId, now.plus(this.inviteTtl)));
		return invitationView(invitation);
	}

	/** Open invitations of the caller's family; visible to every member. */
	public List<InvitationView> sentInvitations(UUID userId) {
		UUID familyId = membership(userId).getFamilyId();
		return this.invitations
			.findByFamilyIdAndStatusAndExpiresAtAfterOrderByCreatedAt(familyId, InvitationStatus.PENDING, Instant.now())
			.stream()
			.map(FamilyService::invitationView)
			.toList();
	}

	@Transactional
	public void revokeInvitation(UUID userId, UUID invitationId) {
		UUID familyId = requireAdmin(userId).getFamilyId();
		Instant now = Instant.now();
		this.invitations.findById(invitationId)
			.filter((invitation) -> invitation.getFamilyId().equals(familyId) && invitation.isOpen(now))
			.orElseThrow(() -> new NotFoundException("Invitation not found"))
			.close(InvitationStatus.REVOKED, now);
	}

	/** Open invitations addressed to the caller's email, from any family. */
	public List<ReceivedInvitationView> receivedInvitations(UUID userId) {
		UserView user = requireVerifiedEmail(userId);
		List<FamilyInvitationEntity> received = this.invitations.findByEmailAndStatusAndExpiresAtAfterOrderByCreatedAt(
				user.email(), InvitationStatus.PENDING, Instant.now());
		Map<UUID, UserView> inviters = usersById(received.stream().map(FamilyInvitationEntity::getInvitedBy).toList());
		return received.stream().map((invitation) -> {
			UUID familyId = invitation.getFamilyId();
			FamilyEntity family = this.families.findById(familyId)
				.orElseThrow(() -> new NotFoundException("Family not found"));
			UserView inviter = inviters.get(invitation.getInvitedBy());
			return new ReceivedInvitationView(invitation.getId(), familyId, family.getName(),
					(inviter != null) ? inviter.displayName() : "", (int) this.members.countByFamilyId(familyId),
					invitation.getExpiresAt());
		}).toList();
	}

	/**
	 * Joins the inviting family. A user belongs to one family only, so the caller first
	 * leaves their current one: as its last member that deletes it, as its only admin the
	 * longest-standing remaining member becomes admin.
	 */
	@Transactional
	public FamilyView accept(UUID userId, UUID invitationId) {
		FamilyInvitationEntity invitation = receivedInvitation(userId, invitationId);
		Instant now = Instant.now();
		UUID familyId = invitation.getFamilyId();
		lockFamily(familyId);
		Optional<FamilyMemberEntity> current = this.members.findById(userId);
		if (current.isPresent() && current.get().getFamilyId().equals(familyId)) {
			invitation.close(InvitationStatus.ACCEPTED, now);
			return view(familyId);
		}
		// The accepted invitation's own seat becomes the new member's seat.
		if (seatsTaken(familyId, now) > MAX_MEMBERS) {
			throw familyFull();
		}
		if (current.isPresent()) {
			moveToFamily(current.get(), familyId);
		}
		else {
			this.members.save(new FamilyMemberEntity(userId, familyId, FamilyRole.MEMBER));
		}
		invitation.close(InvitationStatus.ACCEPTED, now);
		return view(familyId);
	}

	@Transactional
	public void decline(UUID userId, UUID invitationId) {
		receivedInvitation(userId, invitationId).close(InvitationStatus.DECLINED, Instant.now());
	}

	/** Leaving as the last member deletes the family; the last admin must promote someone first. */
	@Transactional
	public void leave(UUID userId) {
		FamilyMemberEntity member = membership(userId);
		UUID familyId = member.getFamilyId();
		List<FamilyMemberEntity> all = this.members.findByFamilyIdOrderByJoinedAt(familyId);
		if (all.size() == 1) {
			this.members.delete(member);
			this.families.deleteById(familyId);
			return;
		}
		requireNotLastAdmin(member);
		this.members.delete(member);
	}

	@Transactional
	public void removeMember(UUID userId, UUID memberUserId) {
		FamilyMemberEntity admin = requireAdmin(userId);
		if (userId.equals(memberUserId)) {
			throw new ApiException(HttpStatus.CONFLICT, "cannot-remove-self", "Use leave to remove yourself");
		}
		this.members.delete(memberOfSameFamily(admin, memberUserId));
	}

	@Transactional
	public FamilyView changeRole(UUID userId, UUID memberUserId, FamilyRole role) {
		FamilyMemberEntity admin = requireAdmin(userId);
		FamilyMemberEntity target = memberOfSameFamily(admin, memberUserId);
		if (role == FamilyRole.MEMBER) {
			requireNotLastAdmin(target);
		}
		target.setRole(role);
		return view(admin.getFamilyId());
	}

	private void moveToFamily(FamilyMemberEntity member, UUID newFamilyId) {
		UUID oldFamilyId = member.getFamilyId();
		List<FamilyMemberEntity> others = this.members.findByFamilyIdOrderByJoinedAt(oldFamilyId)
			.stream()
			.filter((other) -> !other.getUserId().equals(member.getUserId()))
			.toList();
		if (!others.isEmpty() && member.getRole() == FamilyRole.ADMIN
				&& others.stream().noneMatch((other) -> other.getRole() == FamilyRole.ADMIN)) {
			others.getFirst().setRole(FamilyRole.ADMIN);
		}
		member.moveTo(newFamilyId);
		if (others.isEmpty()) {
			// Move the membership row out before the family's delete cascades to its members.
			this.members.flush();
			this.families.deleteById(oldFamilyId);
		}
	}

	private FamilyInvitationEntity receivedInvitation(UUID userId, UUID invitationId) {
		UserView user = requireVerifiedEmail(userId);
		Instant now = Instant.now();
		return this.invitations.findById(invitationId)
			.filter((invitation) -> invitation.getEmail().equals(user.email()) && invitation.isOpen(now))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "invalid-invite",
					"Invitation is invalid, expired or already answered"));
	}

	/** Invitations are keyed by email, so only someone who proved they own it may use them. */
	private UserView requireVerifiedEmail(UUID userId) {
		if (!this.userService.hasVerifiedEmail(userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "email-not-verified",
					"Sign in with Google to confirm your email before using invitations");
		}
		return this.userService.get(userId);
	}

	private long seatsTaken(UUID familyId, Instant now) {
		return this.members.countByFamilyId(familyId)
				+ this.invitations.countByFamilyIdAndStatusAndExpiresAtAfter(familyId, InvitationStatus.PENDING, now);
	}

	private void lockFamily(UUID familyId) {
		this.families.findByIdForUpdate(familyId).orElseThrow(() -> new NotFoundException("Family not found"));
	}

	private static ApiException familyFull() {
		return new ApiException(HttpStatus.CONFLICT, "family-full",
				"A family can have at most " + MAX_MEMBERS + " members, pending invitations included");
	}

	private FamilyMemberEntity membership(UUID userId) {
		return this.members.findById(userId).orElseThrow(() -> new NotFoundException("You are not in a family"));
	}

	private void requireNoFamily(UUID userId) {
		if (this.members.existsById(userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "already-in-family", "You already belong to a family");
		}
	}

	private FamilyMemberEntity requireAdmin(UUID userId) {
		FamilyMemberEntity member = membership(userId);
		if (member.getRole() != FamilyRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "admin-required", "Only family admins can do this");
		}
		return member;
	}

	private FamilyMemberEntity memberOfSameFamily(FamilyMemberEntity admin, UUID memberUserId) {
		return this.members.findById(memberUserId)
			.filter((member) -> member.getFamilyId().equals(admin.getFamilyId()))
			.orElseThrow(() -> new NotFoundException("Member not found in your family"));
	}

	private void requireNotLastAdmin(FamilyMemberEntity member) {
		if (member.getRole() == FamilyRole.ADMIN
				&& this.members.countByFamilyIdAndRole(member.getFamilyId(), FamilyRole.ADMIN) == 1) {
			throw new ApiException(HttpStatus.CONFLICT, "last-admin",
					"A family needs at least one admin; promote another member first");
		}
	}

	private Map<UUID, UserView> usersById(List<UUID> ids) {
		return this.userService.getAll(ids).stream().collect(Collectors.toMap(UserView::id, Function.identity()));
	}

	private FamilyView view(UUID familyId) {
		FamilyEntity family = this.families.findById(familyId).orElseThrow(() -> new NotFoundException("Family not found"));
		List<FamilyMemberEntity> familyMembers = this.members.findByFamilyIdOrderByJoinedAt(familyId);
		Map<UUID, UserView> users = usersById(familyMembers.stream().map(FamilyMemberEntity::getUserId).toList());
		List<FamilyView.Member> memberViews = familyMembers.stream().map((member) -> {
			UserView user = users.get(member.getUserId());
			return new FamilyView.Member(member.getUserId(), user.email(), user.displayName(), member.getRole(),
					member.getJoinedAt());
		}).toList();
		return new FamilyView(familyId, family.getName(), memberViews);
	}

	private static InvitationView invitationView(FamilyInvitationEntity invitation) {
		return new InvitationView(invitation.getId(), invitation.getEmail(), invitation.getCreatedAt(),
				invitation.getExpiresAt());
	}

}
