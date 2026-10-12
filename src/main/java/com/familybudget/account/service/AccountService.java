package com.familybudget.account.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.account.entity.AccountEntity;
import com.familybudget.account.model.AccountAccess;
import com.familybudget.account.model.AccountRef;
import com.familybudget.account.model.AccountRequest;
import com.familybudget.account.model.AccountView;
import com.familybudget.account.model.GrantLevel;
import com.familybudget.account.model.TransientAccountView;
import com.familybudget.account.repository.AccountRepository;
import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.IdConflictException;
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.common.model.SyncChanges;
import com.familybudget.common.model.SyncWindow;
import com.familybudget.common.service.SyncTombstones;
import com.familybudget.common.service.UserOrder;
import com.familybudget.family.service.FamilyService;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

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

	private final EntityManager entityManager;

	private final UserOrder userOrder;

	private final SyncTombstones tombstones;

	private static final UserOrder.Table POSITIONS = new UserOrder.Table("account_positions", "account_id");

	AccountService(AccountRepository accounts, FamilyService familyService, UserService userService,
			EntityManager entityManager, UserOrder userOrder, SyncTombstones tombstones) {
		this.accounts = accounts;
		this.familyService = familyService;
		this.userService = userService;
		this.entityManager = entityManager;
		this.userOrder = userOrder;
		this.tombstones = tombstones;
	}

	/**
	 * Open accounts the user can see, in their own order (new ones last, oldest first), or
	 * with {@code closed} their own closed ones, latest first.
	 */
	public List<AccountView> list(UUID userId, boolean closed) {
		return views(userId, closed ? this.accounts.findClosedOwnedBy(userId)
				: this.userOrder.sort(POSITIONS, userId, this.accounts.findOpenViewable(userId), AccountEntity::getId));
	}

	/**
	 * Sync feed (ARCHITECTURE.md §6.5): accounts written in {@code window} as {@link #list}
	 * would show them. Ones the user no longer sees there (closed by their owner, say)
	 * count as deleted, like the ones actually deleted.
	 */
	public SyncChanges<AccountView> changes(UUID userId, SyncWindow window) {
		List<UUID> ids = this.accounts.findChangedIds(userId, window.since(), window.until(), window.limit());
		List<UUID> tombstones = this.tombstones.deleted("account", List.of(userId), window);
		if (SyncChanges.exceeds(window, ids.size(), tombstones.size())) {
			return SyncChanges.tooMany();
		}
		List<AccountEntity> candidates = this.accounts.findByIdIn(ids);
		List<AccountEntity> listed = candidates.stream().filter((account) -> isListed(userId, account)).toList();
		Set<UUID> listedIds = listed.stream().map(AccountEntity::getId).collect(Collectors.toSet());
		Set<UUID> deleted = new LinkedHashSet<>();
		candidates.stream().map(AccountEntity::getId).filter((id) -> !listedIds.contains(id)).forEach(deleted::add);
		tombstones.stream().filter((id) -> !listedIds.contains(id)).forEach(deleted::add);
		return new SyncChanges<>(views(userId, listed), List.copyOf(deleted));
	}

	/** Sync feed: the ids {@link #list} returns, in the user's order. */
	public List<UUID> order(UUID userId) {
		return list(userId, false).stream().map(AccountView::id).toList();
	}

	/** The user's own order of the open accounts they see. */
	@Transactional
	public void reorder(UUID userId, List<UUID> ids) {
		this.userOrder.replace(POSITIONS, userId, ids,
				this.accounts.findOpenViewable(userId).stream().map(AccountEntity::getId).toList());
	}

	public AccountView get(UUID userId, UUID id) {
		return view(userId, viewable(userId, id));
	}

	@Transactional
	public AccountView create(UUID userId, AccountRequest request) {
		if (request.id() != null) {
			this.tombstones.requireNotDeleted("account", request.id());
			Optional<AccountEntity> existing = this.accounts.findById(request.id());
			if (existing.isPresent()) {
				return replay(userId, existing.get());
			}
		}
		AccountEntity account = new AccountEntity(userId);
		account.useClientId(request.id());
		account.apply(request, grants(userId, request.grants()));
		return view(userId, this.accounts.save(account));
	}

	@Transactional
	public AccountView update(UUID userId, UUID id, AccountRequest request) {
		AccountEntity account = owned(userId, id);
		requireOpen(account);
		account.apply(request, grants(userId, request.grants()));
		return view(userId, account);
	}

	/** Only accounts without transactions; anything else has to be closed instead. */
	@Transactional
	public void delete(UUID userId, UUID id) {
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
		requireRegular(account);
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
		// Bulk updates bypass loaded entities; reload this one so later reads see it closed.
		AccountEntity loaded = this.entityManager.find(AccountEntity.class, id);
		if (loaded != null) {
			this.entityManager.refresh(loaded);
		}
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

	/** What {@link #list} shows: open accounts the user may view, closed ones they own. */
	private static boolean isListed(UUID userId, AccountEntity account) {
		AccountAccess access = account.accessFor(userId);
		return account.isClosed() ? access == AccountAccess.OWNER : access.canView();
	}

	/** A create sent again with the same client id returns what the first one made. */
	private AccountView replay(UUID userId, AccountEntity account) {
		if (!account.accessFor(userId).canView()) {
			throw new IdConflictException();
		}
		return view(userId, account);
	}

	private AccountEntity owned(UUID userId, UUID id) {
		AccountEntity account = viewable(userId, id);
		if (account.accessFor(userId) != AccountAccess.OWNER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not-account-owner", "Only the account's owner can do this");
		}
		requireRegular(account);
		return account;
	}

	/** Transient accounts only move through family transfers (send, collect, take back). */
	private static void requireRegular(AccountEntity account) {
		if (account.isTransient()) {
			throw new ApiException(HttpStatus.CONFLICT, "transient-account",
					"Money in transit between family members only moves through family transfers");
		}
	}

	/** An open regular account the user owns: where family transfers start and end. */
	public AccountRef requireOwnOpen(UUID userId, UUID id) {
		AccountEntity account = owned(userId, id);
		requireOpen(account);
		return account.toRef(userId);
	}

	/** The open transient account from sender to recipient in this currency, created if missing. */
	@Transactional
	public TransientAccountView openTransient(UUID senderId, UUID recipientId, String currency) {
		String name = this.userService.get(senderId).displayName() + " → "
				+ this.userService.get(recipientId).displayName();
		this.accounts.insertTransientIfAbsent(senderId, recipientId, currency, name);
		return this.accounts.findOpenTransient(senderId, recipientId, currency)
			.orElseThrow(() -> new IllegalStateException("Transient account was not created"))
			.toTransientView();
	}

	public TransientAccountView requireOpenTransient(UUID id) {
		return this.accounts.findById(id)
			.filter((account) -> account.isTransient() && !account.isClosed())
			.orElseThrow(() -> new NotFoundException("Transfer not found"))
			.toTransientView();
	}

	/** Open transient accounts the user sends through or receives from. */
	public List<TransientAccountView> openTransientsInvolving(UUID userId) {
		return this.accounts.findOpenTransientsInvolving(userId)
			.stream()
			.map(AccountEntity::toTransientView)
			.toList();
	}

	/** Takes {@code amount} out of a transient account, never below zero. */
	@Transactional
	public void withdrawFromTransient(UUID id, BigDecimal amount) {
		if (this.accounts.withdraw(id, amount) != 1) {
			throw new ApiException(HttpStatus.CONFLICT, "insufficient-in-transit",
					"There isn't that much money waiting in this transfer");
		}
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
