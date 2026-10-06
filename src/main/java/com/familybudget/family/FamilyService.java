package com.familybudget.family;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	// No 0/O/1/I so codes survive being read out loud or retyped.
	private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private static final int CODE_LENGTH = 8;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final FamilyRepository families;

	private final FamilyMemberRepository members;

	private final FamilyInviteRepository invites;

	private final UserService userService;

	private final Duration inviteTtl;

	FamilyService(FamilyRepository families, FamilyMemberRepository members, FamilyInviteRepository invites,
			UserService userService, @Value("${app.family.invite-ttl}") Duration inviteTtl) {
		this.families = families;
		this.members = members;
		this.invites = invites;
		this.userService = userService;
		this.inviteTtl = inviteTtl;
	}

	/** The caller's family, used by later modules to scope shared data (ARCHITECTURE.md §6.3). */
	public Optional<UUID> currentFamilyId(UUID userId) {
		return this.members.findById(userId).map(FamilyMemberEntity::getFamilyId);
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
	public InviteView createInvite(UUID userId) {
		FamilyMemberEntity admin = requireAdmin(userId);
		FamilyInviteEntity invite = this.invites.save(new FamilyInviteEntity(newCode(), admin.getFamilyId(), userId,
				Instant.now().plus(this.inviteTtl)));
		return new InviteView(invite.getCode(), invite.getExpiresAt());
	}

	@Transactional
	public FamilyView join(UUID userId, String code) {
		requireNoFamily(userId);
		Instant now = Instant.now();
		FamilyInviteEntity invite = this.invites.findById(code.strip().toUpperCase(Locale.ROOT))
			.filter((candidate) -> candidate.isUsable(now))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "invalid-invite",
					"Invite code is invalid, expired or already used"));
		invite.markUsed(userId, now);
		this.members.save(new FamilyMemberEntity(userId, invite.getFamilyId(), FamilyRole.MEMBER));
		return view(invite.getFamilyId());
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

	private FamilyView view(UUID familyId) {
		FamilyEntity family = this.families.findById(familyId).orElseThrow(() -> new NotFoundException("Family not found"));
		List<FamilyMemberEntity> familyMembers = this.members.findByFamilyIdOrderByJoinedAt(familyId);
		Map<UUID, UserView> users = this.userService.getAll(familyMembers.stream().map(FamilyMemberEntity::getUserId).toList())
			.stream()
			.collect(Collectors.toMap(UserView::id, Function.identity()));
		List<FamilyView.Member> memberViews = familyMembers.stream().map((member) -> {
			UserView user = users.get(member.getUserId());
			return new FamilyView.Member(member.getUserId(), user.email(), user.displayName(), member.getRole(),
					member.getJoinedAt());
		}).toList();
		return new FamilyView(familyId, family.getName(), memberViews);
	}

	private static String newCode() {
		char[] code = new char[CODE_LENGTH];
		for (int i = 0; i < CODE_LENGTH; i++) {
			code[i] = CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)];
		}
		return new String(code);
	}

}
