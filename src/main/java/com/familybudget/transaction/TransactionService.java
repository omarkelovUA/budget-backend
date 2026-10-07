package com.familybudget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.account.AccountAccess;
import com.familybudget.account.AccountRef;
import com.familybudget.account.AccountService;
import com.familybudget.account.AccountView;
import com.familybudget.category.CategoryService;
import com.familybudget.category.CategoryType;
import com.familybudget.category.CategoryView;
import com.familybudget.common.AccessScope;
import com.familybudget.common.ApiException;
import com.familybudget.common.NotFoundException;
import com.familybudget.common.PageView;
import com.familybudget.family.FamilyService;

/**
 * Public API of the transaction module. Recording or deleting a transaction moves the
 * touched accounts' balances in the same database transaction.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("createdAt"));

	private final TransactionRepository transactions;

	private final AccountService accountService;

	private final CategoryService categoryService;

	private final FamilyService familyService;

	TransactionService(TransactionRepository transactions, AccountService accountService,
			CategoryService categoryService, FamilyService familyService) {
		this.transactions = transactions;
		this.accountService = accountService;
		this.categoryService = categoryService;
		this.familyService = familyService;
	}

	PageView<TransactionView> list(UUID userId, @Nullable UUID accountId, int page, int size) {
		List<UUID> accountIds = (accountId != null) ? List.of(this.accountService.requireViewable(userId, accountId).id())
				: this.accountService.viewableIds(userId);
		PageRequest pageRequest = PageRequest.of(page, size, NEWEST_FIRST);
		if (accountIds.isEmpty()) {
			return PageView.of(Page.<TransactionEntity>empty(pageRequest), (entities) -> toViews(userId, entities));
		}
		return PageView.of(this.transactions.findTouching(accountIds, pageRequest),
				(entities) -> toViews(userId, entities));
	}

	/**
	 * Income and expense per currency for the payments the user can see in a month. Never
	 * summed across currencies: v1 has no FX (ARCHITECTURE.md §7).
	 */
	List<MonthSummaryView> monthSummary(UUID userId, YearMonth period) {
		List<UUID> accountIds = this.accountService.viewableIds(userId);
		if (accountIds.isEmpty()) {
			return List.of();
		}
		List<Object[]> rows = this.transactions.sumPaymentsByAccountAndCategory(accountIds, period.atDay(1),
				period.plusMonths(1).atDay(1));
		Set<UUID> categoryIds = new HashSet<>();
		rows.forEach((row) -> categoryIds.add((UUID) row[1]));
		Map<UUID, AccountRef> accounts = this.accountService.refs(userId, accountIds);
		Map<UUID, CategoryView> categories = this.categoryService.views(categoryIds);
		Map<String, BigDecimal[]> totals = new TreeMap<>();
		for (Object[] row : rows) {
			String currency = accounts.get((UUID) row[0]).currency();
			BigDecimal[] sums = totals.computeIfAbsent(currency, (c) -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
			int index = (categories.get((UUID) row[1]).type() == CategoryType.INCOME) ? 0 : 1;
			sums[index] = sums[index].add((BigDecimal) row[2]);
		}
		return totals.entrySet()
			.stream()
			.map((entry) -> new MonthSummaryView(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
			.toList();
	}

	@Transactional
	TransactionView create(UUID userId, TransactionRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		TransactionEntity transaction = switch (request.kind()) {
			case PAYMENT -> recordPayment(scope, request);
			case TRANSFER -> recordTransfer(scope, request);
		};
		return toViews(userId, List.of(this.transactions.save(transaction))).getFirst();
	}

	/**
	 * Closes an account its owner no longer needs: the remaining balance moves to
	 * {@code targetAccountId} (same currency, usable by the owner) as a transfer, then the
	 * account is archived. A negative balance is pulled from the target, so it ends at zero.
	 */
	@Transactional
	AccountView closeAccount(UUID userId, UUID accountId, @Nullable UUID targetAccountId) {
		AccountView account = this.accountService.requireOwnedOpen(userId, accountId);
		int sign = account.balance().signum();
		if (sign != 0) {
			if (targetAccountId == null || targetAccountId.equals(accountId)) {
				throw new ApiException(HttpStatus.CONFLICT, "close-target-required",
						"Pick another account to move the remaining balance to");
			}
			AccountRef target = this.accountService.requireUsable(userId, targetAccountId);
			requireSameCurrency(account.currency(), target.currency());
			UUID from = (sign > 0) ? accountId : target.id();
			UUID to = (sign > 0) ? target.id() : accountId;
			BigDecimal amount = account.balance().abs();
			this.accountService.adjustBalance(from, amount.negate());
			this.accountService.adjustBalance(to, amount);
			this.transactions.save(TransactionEntity.transfer(amount, LocalDate.now(), userId, null, from, to));
		}
		this.accountService.markClosed(accountId);
		return this.accountService.get(userId, accountId);
	}

	@Transactional
	void delete(UUID userId, UUID id) {
		TransactionEntity transaction = this.transactions.findById(id)
			.orElseThrow(() -> new NotFoundException("Transaction not found"));
		List<UUID> touched = transaction.accountIds();
		Collection<AccountRef> accounts = this.accountService.refs(userId, touched).values();
		if (accounts.stream().noneMatch((account) -> account.access().canView())) {
			throw new NotFoundException("Transaction not found");
		}
		// Deleting would change a closed account's frozen balance.
		if (accounts.stream().anyMatch(AccountRef::closed)) {
			throw new ApiException(HttpStatus.CONFLICT, "account-closed",
					"This transaction touches a closed account");
		}
		if (!isEditable(transaction, accounts, userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "transaction-not-editable",
					"Only whoever recorded this transaction or the account's owner can delete it,"
							+ " and only with shared access to every account it touches");
		}
		if (transaction.getKind() == TransactionKind.PAYMENT) {
			CategoryView category = this.categoryService.views(List.of(TransactionEntity.require(transaction.getCategoryId())))
				.values()
				.iterator()
				.next();
			this.accountService.adjustBalance(touched.getFirst(), signed(category.type(), transaction.getAmount()).negate());
		}
		else {
			this.accountService.adjustBalance(touched.get(0), transaction.getAmount());
			this.accountService.adjustBalance(touched.get(1), transaction.getAmount().negate());
		}
		this.transactions.delete(transaction);
	}

	/**
	 * Total of payments in a category during a month, optionally only those recorded by one
	 * user. Backs {@code Budget.factAmount}.
	 */
	public BigDecimal sumPayments(UUID categoryId, YearMonth period, @Nullable UUID recordedBy) {
		var from = period.atDay(1);
		var to = period.plusMonths(1).atDay(1);
		return (recordedBy != null) ? this.transactions.sumPaymentsRecordedBy(categoryId, from, to, recordedBy)
				: this.transactions.sumPayments(categoryId, from, to);
	}

	private TransactionEntity recordPayment(AccessScope scope, TransactionRequest request) {
		if (request.accountId() == null || request.categoryId() == null || request.fromAccountId() != null
				|| request.toAccountId() != null) {
			throw invalid("A payment needs accountId and categoryId, and no transfer accounts");
		}
		AccountRef account = this.accountService.requireUsable(scope.userId(), request.accountId());
		CategoryView category = this.categoryService.requireAccessible(scope, request.categoryId());
		this.accountService.adjustBalance(account.id(), signed(category.type(), request.amount()));
		return TransactionEntity.payment(request.amount(), request.date(), scope.userId(), request.note(), account.id(),
				category.id());
	}

	private TransactionEntity recordTransfer(AccessScope scope, TransactionRequest request) {
		if (request.fromAccountId() == null || request.toAccountId() == null || request.accountId() != null
				|| request.categoryId() != null) {
			throw invalid("A transfer needs fromAccountId and toAccountId, and no account or category");
		}
		if (request.fromAccountId().equals(request.toAccountId())) {
			throw invalid("A transfer needs two different accounts");
		}
		// Both sides: "visible" means no transactions at all, in either direction.
		AccountRef from = this.accountService.requireUsable(scope.userId(), request.fromAccountId());
		AccountRef to = this.accountService.requireUsable(scope.userId(), request.toAccountId());
		requireSameCurrency(from.currency(), to.currency());
		this.accountService.adjustBalance(from.id(), request.amount().negate());
		this.accountService.adjustBalance(to.id(), request.amount());
		return TransactionEntity.transfer(request.amount(), request.date(), scope.userId(), request.note(), from.id(),
				to.id());
	}

	/** v1: one currency per account and no FX conversion (ARCHITECTURE.md §7). */
	private static void requireSameCurrency(String from, String to) {
		if (!from.equals(to)) {
			throw new ApiException(HttpStatus.CONFLICT, "currency-mismatch",
					"Transfers between accounts in different currencies aren't supported");
		}
	}

	/**
	 * Shared access (or ownership) on every open account it touches, and the caller recorded
	 * it or owns one of those accounts (PLAN.md open decision #7).
	 */
	private static boolean isEditable(TransactionEntity transaction, Collection<AccountRef> accounts, UUID userId) {
		boolean usable = accounts.size() == transaction.accountIds().size()
				&& accounts.stream().allMatch((account) -> account.access().canUse() && !account.closed());
		return usable && (transaction.getRecordedBy().equals(userId)
				|| accounts.stream().anyMatch((account) -> account.access() == AccountAccess.OWNER));
	}

	private List<TransactionView> toViews(UUID userId, List<TransactionEntity> entities) {
		Set<UUID> accountIds = new HashSet<>();
		Set<UUID> categoryIds = new HashSet<>();
		for (TransactionEntity entity : entities) {
			accountIds.addAll(entity.accountIds());
			if (entity.getCategoryId() != null) {
				categoryIds.add(entity.getCategoryId());
			}
		}
		Map<UUID, AccountRef> accounts = this.accountService.refs(userId, accountIds);
		Map<UUID, CategoryView> categories = this.categoryService.views(categoryIds);
		return entities.stream().map((entity) -> toView(userId, entity, accounts, categories)).toList();
	}

	private static TransactionView toView(UUID userId, TransactionEntity entity, Map<UUID, AccountRef> accounts,
			Map<UUID, CategoryView> categories) {
		AccountRef account = lookup(accounts, entity.getAccountId());
		AccountRef fromAccount = lookup(accounts, entity.getFromAccountId());
		AccountRef toAccount = lookup(accounts, entity.getToAccountId());
		List<AccountRef> touched = entity.accountIds().stream().map(accounts::get).filter(Objects::nonNull).toList();
		return new TransactionView(entity.getId(), entity.getKind(), entity.getAmount(), entity.getOccurredOn(),
				entity.getNote(), entity.getRecordedBy(), account, lookup(categories, entity.getCategoryId()),
				fromAccount, toAccount, isEditable(entity, touched, userId));
	}

	private static <T> @Nullable T lookup(Map<UUID, T> values, @Nullable UUID id) {
		return (id != null) ? values.get(id) : null;
	}

	private static BigDecimal signed(CategoryType type, BigDecimal amount) {
		return (type == CategoryType.INCOME) ? amount : amount.negate();
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "invalid-transaction", message);
	}

}
