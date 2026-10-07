package com.familybudget.account;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.ApiException;
import com.familybudget.common.NotFoundException;
import com.familybudget.family.FamilyService;
import com.familybudget.user.UserService;
import com.familybudget.user.UserView;

/**
 * Public API of the account module. Every account has one owner, who gives family members
 * visible or shared access (ARCHITECTURE.md §3.2, §6.3). Only the owner manages it.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

	private final AccountRepository accounts;

	private final FamilyService familyService;

	private final UserService userService;

	AccountService(AccountRepository accounts, FamilyService familyService, UserService userService) {
		this.accounts = accounts;
		this.familyService = familyService;
		this.userService = userService;
	}

	/** Open accounts the user can see, or with {@code closed} their own closed ones. */
	List<AccountView> list(UUID userId, boolean closed) {
		return views(userId,
				closed ? this.accounts.findClosedOwnedBy(userId) : this.accounts.findOpenViewable(userId));
	}

	public AccountView get(UUID userId, UUID id) {
		return view(userId, viewable(userId, id));
	}

	@Transactional
	AccountView create(UUID userId, AccountRequest request) {
		AccountEntity account = new AccountEntity(userId);
		account.apply(request, grants(userId, request.grants()));
		return view(userId, this.accounts.save(account));
	}

	@Transactional
	AccountView update(UUID userId, UUID id, AccountRequest request) {
		AccountEntity account = owned(userId, id);
		requireOpen(account);
		account.apply(request, grants(userId, request.grants()));
		return view(userId, account);
	}

	/** Only accounts without transactions; anything else has to be closed instead. */
	@Transactional
	void delete(UUID userId, UUID id) {
		AccountEntity account = owned(userId, id);
		try {
			this.accounts.delete(account);
			this.accounts.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "account-in-use",
					"This account has transactions; close it instead");
		}
	}

	/** An open account the user may record transactions on (owner or shared access). */
	public AccountRef requireUsable(UUID userId, UUID id) {
		AccountEntity account = viewable(userId, id);
		requireOpen(account);
		if (!account.accessFor(userId).canUse()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "account-read-only",
					"This account is only visible to you; you can't record transactions on it");
		}
		return account.toRef(userId);
	}

	public AccountRef requireViewable(UUID userId, UUID id) {
		return viewable(userId, id).toRef(userId);
	}

	/** An open account the user owns, with its balance; the starting point for closing it. */
	public AccountView requireOwnedOpen(UUID userId, UUID id) {
		AccountEntity account = owned(userId, id);
		requireOpen(account);
		return view(userId, account);
	}

	/** Called by the transaction module once the remaining balance has been moved out. */
	@Transactional
	public void markClosed(UUID id) {
		this.accounts.close(id);
	}

	/** Every account the user can see, closed ones included (for history and totals). */
	public List<UUID> viewableIds(UUID userId) {
		return this.accounts.findViewableIds(userId);
	}

	/** Refs with the user's access to each; callers only show them for data the user already sees. */
	public Map<UUID, AccountRef> refs(UUID userId, Collection<UUID> ids) {
		return this.accounts.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(AccountEntity::getId, (account) -> account.toRef(userId), (a, b) -> a));
	}

	@Transactional
	public void adjustBalance(UUID id, BigDecimal delta) {
		if (this.accounts.addToBalance(id, delta) != 1) {
			throw new NotFoundException("Account not found");
		}
	}

	private AccountEntity viewable(UUID userId, UUID id) {
		return this.accounts.findById(id)
			.filter((account) -> account.accessFor(userId).canView())
			.orElseThrow(() -> new NotFoundException("Account not found"));
	}

	private AccountEntity owned(UUID userId, UUID id) {
		AccountEntity account = viewable(userId, id);
		if (account.accessFor(userId) != AccountAccess.OWNER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not-account-owner", "Only the account's owner can do this");
		}
		return account;
	}

	private static void requireOpen(AccountEntity account) {
		if (account.isClosed()) {
			throw new ApiException(HttpStatus.CONFLICT, "account-closed", "This account is closed");
		}
	}

	/** Grants may only go to other current members of the owner's family. */
	private Map<UUID, GrantLevel> grants(UUID ownerId, @Nullable List<AccountRequest.GrantRequest> requested) {
		if (requested == null || requested.isEmpty()) {
			return Map.of();
		}
		UUID familyId = this.familyService.currentFamilyId(ownerId)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "no-family",
					"Join or create a family to share an account"));
		Set<UUID> members = this.familyService.memberIds(familyId);
		Map<UUID, GrantLevel> grants = new LinkedHashMap<>();
		for (AccountRequest.GrantRequest grant : requested) {
			if (grant.userId().equals(ownerId) || !members.contains(grant.userId())) {
				throw new ApiException(HttpStatus.CONFLICT, "grantee-not-in-family",
						"Accounts can only be shared with other members of your family");
			}
			grants.put(grant.userId(), grant.level());
		}
		return grants;
	}

	private AccountView view(UUID userId, AccountEntity account) {
		return views(userId, List.of(account)).getFirst();
	}

	private List<AccountView> views(UUID userId, List<AccountEntity> entities) {
		Set<UUID> userIds = new HashSet<>();
		for (AccountEntity account : entities) {
			userIds.add(account.getOwnerUserId());
			if (account.accessFor(userId) == AccountAccess.OWNER) {
				userIds.addAll(account.getGrants().keySet());
			}
		}
		Map<UUID, UserView> users = this.userService.getAll(userIds)
			.stream()
			.collect(Collectors.toMap(UserView::id, Function.identity()));
		return entities.stream().map((account) -> {
			AccountAccess access = account.accessFor(userId);
			List<AccountView.Grant> grants = (access != AccountAccess.OWNER) ? null
					: account.getGrants()
						.entrySet()
						.stream()
						.map((grant) -> new AccountView.Grant(grant.getKey(), displayName(users, grant.getKey()),
								grant.getValue()))
						.toList();
			return new AccountView(account.getId(), account.getName(), account.getBalance(), account.getCurrency(),
					account.getIcon(), account.getColor(),
					new AccountView.Owner(account.getOwnerUserId(), displayName(users, account.getOwnerUserId())),
					access, grants, account.isClosed());
		}).toList();
	}

	private static String displayName(Map<UUID, UserView> users, UUID userId) {
		UserView user = users.get(userId);
		return (user != null) ? user.displayName() : "";
	}

}
