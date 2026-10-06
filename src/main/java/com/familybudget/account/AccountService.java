package com.familybudget.account;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.common.AccessScope;
import com.familybudget.common.ApiException;
import com.familybudget.common.NotFoundException;
import com.familybudget.common.Visibility;
import com.familybudget.family.FamilyService;

/** Public API of the account module; every query is scoped per ARCHITECTURE.md §6.3. */
@Service
@Transactional(readOnly = true)
public class AccountService {

	private final AccountRepository accounts;

	private final FamilyService familyService;

	AccountService(AccountRepository accounts, FamilyService familyService) {
		this.accounts = accounts;
		this.familyService = familyService;
	}

	public List<AccountView> list(UUID userId) {
		AccessScope scope = this.familyService.scopeFor(userId);
		return this.accounts.findVisible(scope.userId(), scope.familyId()).stream().map(AccountEntity::toView).toList();
	}

	public AccountView get(UUID userId, UUID id) {
		return accessible(this.familyService.scopeFor(userId), id).toView();
	}

	@Transactional
	AccountView create(UUID userId, AccountRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		AccountEntity account = new AccountEntity(AccessScope.ownerTypeFor(request.visibility()),
				scope.ownerIdFor(request.visibility()));
		account.apply(request);
		return this.accounts.save(account).toView();
	}

	@Transactional
	AccountView update(UUID userId, UUID id, AccountRequest request) {
		AccountEntity account = accessible(this.familyService.scopeFor(userId), id);
		if (Visibility.of(account.getOwnerType()) != request.visibility()) {
			throw new ApiException(HttpStatus.CONFLICT, "visibility-immutable",
					"An account can't be moved between private and shared");
		}
		account.apply(request);
		return account.toView();
	}

	@Transactional
	void delete(UUID userId, UUID id) {
		AccountEntity account = accessible(this.familyService.scopeFor(userId), id);
		try {
			this.accounts.delete(account);
			this.accounts.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "account-in-use",
					"Delete this account's transactions first");
		}
	}

	/** An account the scope may use (and see), for recording transactions on it. */
	public AccountRef requireAccessible(AccessScope scope, UUID id) {
		return accessible(scope, id).toRef();
	}

	/** The subset of {@code ids} the scope may access. */
	public Set<UUID> accessibleIds(AccessScope scope, Collection<UUID> ids) {
		return this.accounts.findByIdIn(ids)
			.stream()
			.filter((account) -> scope.canAccess(account.getOwnerType(), account.getOwnerId()))
			.map(AccountEntity::getId)
			.collect(Collectors.toSet());
	}

	public List<UUID> visibleIds(AccessScope scope) {
		return this.accounts.findVisibleIds(scope.userId(), scope.familyId());
	}

	/** Refs regardless of visibility: callers only use them for data the scope already sees. */
	public Map<UUID, AccountRef> refs(Collection<UUID> ids) {
		return this.accounts.findByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(AccountEntity::getId, AccountEntity::toRef, (a, b) -> a));
	}

	@Transactional
	public void adjustBalance(UUID id, BigDecimal delta) {
		if (this.accounts.addToBalance(id, delta) != 1) {
			throw new NotFoundException("Account not found");
		}
	}

	private AccountEntity accessible(AccessScope scope, UUID id) {
		return this.accounts.findById(id)
			.filter((account) -> scope.canAccess(account.getOwnerType(), account.getOwnerId()))
			.orElseThrow(() -> new NotFoundException("Account not found"));
	}

}
