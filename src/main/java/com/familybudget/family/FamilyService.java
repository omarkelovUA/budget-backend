package com.familybudget.family;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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

	private final FamilyJoinRequestRepository joinRequests;

	private final UserService userService;

	private final ApplicationEventPublisher events;

	private final Duration inviteTtl;

	FamilyService(FamilyRepository families, FamilyMemberRepository members, FamilyInvitationRepository invitations,
			FamilyJoinRequestRepository joinRequests, UserService userService, ApplicationEventPublisher events,
			@Value("${app.family.invite-ttl}") Duration inviteTtl) {
		this.families = families;
		this.members = members;
		this.invitations = invitations;
		this.joinRequests = joinRequests;
		this.userService = userService;
		this.events = events;
		this.inviteTtl = inviteTtl;
	}

	/** The caller's family, used by later modules to scope shared data (ARCHITECTURE.md §6.3). */
	public Optional<UUID> currentFamilyId(UUID userId) {
		return this.members.findById(userId).map(FamilyMemberEntity::getFamilyId);
	}

	public Set<UUID> memberIds(UUID familyId) {
		return this.members.findByFamilyIdOrderByJoinedAt(familyId)
			.stream()
			.map(FamilyMemberEntity::getUserId)
			.collect(Collectors.toSet());
	}

	public boolean isAdmin(UUID userId) {
		return this.members.findById(userId).map((member) -> member.getRole() == FamilyRole.ADMIN).orElse(false);
	}

	/** The caller's family id, refused unless they're its admin. */
	public UUID requireAdminOf(UUID userId) {
		return requireAdmin(userId).getFamilyId();
	}

	public AccessScope scopeFor(UUID userId) {
		return new AccessScope(userId, currentFamilyId(userId).orElse(null));
	}

	public FamilyView current(UUID userId) {
		return view(membership(userId).getFamilyId());
	}

	/** The default name of a user's own family, in their language. */
	static String defaultName(String language) {
		return "uk".equals(language) ? "Родина" : "Family";
	}

	/** A new user's own family, with them as its only member and admin. */
	@Transactional
	void createOwnFamily(UUID userId, String language) {
		this.members.save(new FamilyMemberEntity(userId, newFamily(userId, language), FamilyRole.ADMIN));
	}

	@Transactional
	public FamilyView rename(UUID userId, String name) {
		UUID familyId = requireAdmin(userId).getFamilyId();
		this.families.findById(familyId)
			.orElseThrow(() -> new NotFoundException("Family not found"))
			.rename(name.strip());
		return view(familyId);
	}

	/** The one-time category review is done (or skipped); it isn't offered again. */
	@Transactional
	public void markCategoriesReviewed(UUID userId) {
		UUID familyId = requireAdmin(userId).getFamilyId();
		this.families.findById(familyId)
			.orElseThrow(() -> new NotFoundException("Family not found"))
			.markCategoriesReviewed();
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
	 * longest-standing remaining member becomes admin (see moveOut).
	 */
	@Transactional
	public FamilyView accept(UUID userId, UUID invitationId) {
		FamilyInvitationEntity invitation = receivedInvitation(userId, invitationId);
		Instant now = Instant.now();
		UUID familyId = invitation.getFamilyId();
		lockFamily(familyId);
		FamilyMemberEntity current = membership(userId);
		if (current.getFamilyId().equals(familyId)) {
			invitation.close(InvitationStatus.ACCEPTED, now);
			return view(familyId);
		}
		// The accepted invitation's own seat becomes the new member's seat.
		if (seatsTaken(familyId, now) > MAX_MEMBERS) {
			throw familyFull();
		}
		invitation.close(InvitationStatus.ACCEPTED, now);
		join(current, familyId);
		return view(familyId);
	}

	@Transactional
	public void decline(UUID userId, UUID invitationId) {
		receivedInvitation(userId, invitationId).close(InvitationStatus.DECLINED, Instant.now());
	}

	/**
	 * Leaving puts the caller in a new family of their own (there's nothing to leave while
	 * alone). As the only admin, the longest-standing remaining member takes over.
	 */
	@Transactional
	public FamilyView leave(UUID userId) {
		FamilyMemberEntity member = membership(userId);
		if (this.members.countByFamilyId(member.getFamilyId()) == 1) {
			throw new ApiException(HttpStatus.CONFLICT, "alone-in-family", "You are the only member of your family");
		}
		moveToOwnFamily(member);
		return view(member.getFamilyId());
	}

	/** The removed member gets a new family of their own. */
	@Transactional
	public void removeMember(UUID userId, UUID memberUserId) {
		FamilyMemberEntity admin = requireAdmin(userId);
		if (userId.equals(memberUserId)) {
			throw new ApiException(HttpStatus.CONFLICT, "cannot-remove-self", "Use leave to remove yourself");
		}
		moveToOwnFamily(memberOfSameFamily(admin, memberUserId));
	}

	/**
	 * Asks to join the family of the person with {@code email}. Only someone alone in their
	 * family may ask, with a verified email: the admins decide by who's asking.
	 */
	@Transactional
	public JoinRequestView requestToJoin(UUID userId, String email) {
		requireVerifiedEmail(userId);
		FamilyMemberEntity requester = membership(userId);
		if (this.members.countByFamilyId(requester.getFamilyId()) > 1) {
			throw new ApiException(HttpStatus.CONFLICT, "not-alone",
					"Only someone alone in their family can ask to join another one");
		}
		String normalizedEmail = UserService.normalizeEmail(email);
		UUID familyId = this.userService.findByEmail(normalizedEmail)
			.flatMap((user) -> currentFamilyId(user.id()))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user-not-found",
					"Nobody has signed up with this email"));
		if (familyId.equals(requester.getFamilyId())) {
			throw new ApiException(HttpStatus.CONFLICT, "already-member", "You are already in this family");
		}
		Instant now = Instant.now();
		Optional<FamilyJoinRequestEntity> previous = this.joinRequests.findByRequesterIdAndStatus(userId,
				InvitationStatus.PENDING);
		if (previous.isPresent()) {
			if (previous.get().isOpen(now)) {
				throw new ApiException(HttpStatus.CONFLICT, "already-requested",
						"You already asked to join a family; cancel that request first");
			}
			previous.get().close(InvitationStatus.EXPIRED, now);
			// Free the requester's slot of the partial unique index before inserting.
			this.joinRequests.flush();
		}
		return joinRequestView(this.joinRequests
			.save(new FamilyJoinRequestEntity(userId, familyId, normalizedEmail, now.plus(this.inviteTtl))));
	}

	public Optional<JoinRequestView> ownJoinRequest(UUID userId) {
		return openOwnJoinRequest(userId).map(this::joinRequestView);
	}

	@Transactional
	public void cancelJoinRequest(UUID userId) {
		openOwnJoinRequest(userId).orElseThrow(FamilyService::invalidJoinRequest)
			.close(InvitationStatus.REVOKED, Instant.now());
	}

	/** Open requests to join the caller's family; only its admins answer them. */
	public List<IncomingJoinRequestView> incomingJoinRequests(UUID userId) {
		UUID familyId = requireAdmin(userId).getFamilyId();
		List<FamilyJoinRequestEntity> open = this.joinRequests
			.findByFamilyIdAndStatusAndExpiresAtAfterOrderByCreatedAt(familyId, InvitationStatus.PENDING, Instant.now());
		Map<UUID, UserView> requesters = usersById(open.stream().map(FamilyJoinRequestEntity::getRequesterId).toList());
		return open.stream().map((request) -> {
			UserView requester = requesters.get(request.getRequesterId());
			return new IncomingJoinRequestView(request.getId(), requester.id(), requester.displayName(),
					requester.email(), request.getCreatedAt(), request.getExpiresAt());
		}).toList();
	}

	/** Moves the requester in as a member; their own one-person family is deleted. */
	@Transactional
	public FamilyView approveJoinRequest(UUID userId, UUID requestId) {
		UUID familyId = requireAdmin(userId).getFamilyId();
		lockFamily(familyId);
		FamilyJoinRequestEntity request = incomingJoinRequest(familyId, requestId);
		Instant now = Instant.now();
		if (seatsTaken(familyId, now) >= MAX_MEMBERS) {
			throw familyFull();
		}
		request.close(InvitationStatus.ACCEPTED, now);
		join(membership(request.getRequesterId()), familyId);
		return view(familyId);
	}

	@Transactional
	public void declineJoinRequest(UUID userId, UUID requestId) {
		UUID familyId = requireAdmin(userId).getFamilyId();
		incomingJoinRequest(familyId, requestId).close(InvitationStatus.DECLINED, Instant.now());
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

	/**
	 * Moves a member into {@code familyId}. Requests to join other families that its members
	 * made while alone are moot now.
	 */
	private void join(FamilyMemberEntity member, UUID familyId) {
		moveOut(member, familyId, FamilyRole.MEMBER);
		this.members.flush();
		Instant now = Instant.now();
		this.joinRequests.findByRequesterIdInAndStatus(memberIds(familyId), InvitationStatus.PENDING)
			.forEach((request) -> request.close(InvitationStatus.REVOKED, now));
		this.events.publishEvent(new FamilyMembershipStarted(member.getUserId(), familyId));
	}

	private void moveToOwnFamily(FamilyMemberEntity member) {
		UUID userId = member.getUserId();
		moveOut(member, newFamily(userId, this.userService.settings(userId).language()), FamilyRole.ADMIN);
	}

	private UUID newFamily(UUID userId, String language) {
		return this.families.save(new FamilyEntity(defaultName(language), userId)).getId();
	}

	/**
	 * Moves a member's row to {@code newFamilyId}. As the only admin, the longest-standing
	 * remaining member takes over; as the last member, the old family is deleted.
	 */
	private void moveOut(FamilyMemberEntity member, UUID newFamilyId, FamilyRole role) {
		UUID oldFamilyId = member.getFamilyId();
		List<FamilyMemberEntity> others = this.members.findByFamilyIdOrderByJoinedAt(oldFamilyId)
			.stream()
			.filter((other) -> !other.getUserId().equals(member.getUserId()))
			.toList();
		if (!others.isEmpty() && member.getRole() == FamilyRole.ADMIN
				&& others.stream().noneMatch((other) -> other.getRole() == FamilyRole.ADMIN)) {
			others.getFirst().setRole(FamilyRole.ADMIN);
		}
		member.moveTo(newFamilyId, role);
		if (others.isEmpty()) {
			// Move the membership row out before the family's delete cascades to its members.
			this.members.flush();
			deleteFamily(oldFamilyId, member.getUserId());
		}
		membershipEnded(member.getUserId(), oldFamilyId);
	}

	private void deleteFamily(UUID familyId, UUID lastMemberId) {
		this.families.deleteById(familyId);
		this.events.publishEvent(new FamilyDeleted(familyId, lastMemberId));
	}

	private void membershipEnded(UUID userId, UUID familyId) {
		this.events.publishEvent(new FamilyMembershipEnded(userId, familyId));
	}

	private Optional<FamilyJoinRequestEntity> openOwnJoinRequest(UUID userId) {
		Instant now = Instant.now();
		return this.joinRequests.findByRequesterIdAndStatus(userId, InvitationStatus.PENDING)
			.filter((request) -> request.isOpen(now));
	}

	private FamilyJoinRequestEntity incomingJoinRequest(UUID familyId, UUID requestId) {
		Instant now = Instant.now();
		return this.joinRequests.findById(requestId)
			.filter((request) -> request.getFamilyId().equals(familyId) && request.isOpen(now))
			.orElseThrow(FamilyService::invalidJoinRequest);
	}

	private static ApiException invalidJoinRequest() {
		return new ApiException(HttpStatus.NOT_FOUND, "invalid-join-request",
				"Join request is invalid, expired or already answered");
	}

	private JoinRequestView joinRequestView(FamilyJoinRequestEntity request) {
		String familyName = this.families.findById(request.getFamilyId())
			.map(FamilyEntity::getName)
			.orElseThrow(() -> new NotFoundException("Family not found"));
		return new JoinRequestView(request.getId(), request.getEmail(), familyName, request.getCreatedAt(),
				request.getExpiresAt());
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
		return new FamilyView(familyId, family.getName(), memberViews, family.categoriesReviewed());
	}

	private static InvitationView invitationView(FamilyInvitationEntity invitation) {
		return new InvitationView(invitation.getId(), invitation.getEmail(), invitation.getCreatedAt(),
				invitation.getExpiresAt());
	}

}
