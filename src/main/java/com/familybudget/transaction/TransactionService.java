package com.familybudget.transaction;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.account.AccountRef;
import com.familybudget.account.AccountService;
import com.familybudget.category.CategoryService;
import com.familybudget.category.CategoryType;
import com.familybudget.category.CategoryView;
import com.familybudget.common.AccessScope;
import com.familybudget.common.ApiException;
import com.familybudget.common.NotFoundException;
import com.familybudget.common.PageView;
import com.familybudget.common.Visibility;
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
		AccessScope scope = this.familyService.scopeFor(userId);
		List<UUID> accountIds = (accountId != null) ? List.of(this.accountService.requireAccessible(scope, accountId).id())
				: this.accountService.visibleIds(scope);
		PageRequest pageRequest = PageRequest.of(page, size, NEWEST_FIRST);
		if (accountIds.isEmpty()) {
			return PageView.of(Page.<TransactionEntity>empty(pageRequest), this::toViews);
		}
		return PageView.of(this.transactions.findTouching(accountIds, pageRequest), this::toViews);
	}

	@Transactional
	TransactionView create(UUID userId, TransactionRequest request) {
		AccessScope scope = this.familyService.scopeFor(userId);
		TransactionEntity transaction = switch (request.kind()) {
			case PAYMENT -> recordPayment(scope, request);
			case TRANSFER -> recordTransfer(scope, request);
		};
		return toViews(List.of(this.transactions.save(transaction))).getFirst();
	}

	@Transactional
	void delete(UUID userId, UUID id) {
		AccessScope scope = this.familyService.scopeFor(userId);
		TransactionEntity transaction = this.transactions.findById(id)
			.orElseThrow(() -> new NotFoundException("Transaction not found"));
		List<UUID> touched = transaction.accountIds();
		Set<UUID> accessible = this.accountService.accessibleIds(scope, touched);
		if (accessible.isEmpty()) {
			throw new NotFoundException("Transaction not found");
		}
		// e.g. a family member's transfer from their private account into a shared one.
		if (!accessible.containsAll(touched)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "transaction-not-editable",
					"This transaction touches an account you can't access");
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
		AccountRef account = this.accountService.requireAccessible(scope, request.accountId());
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
		AccountRef from = this.accountService.requireAccessible(scope, request.fromAccountId());
		AccountRef to = this.accountService.requireAccessible(scope, request.toAccountId());
		// v1: one currency per account and no FX conversion (ARCHITECTURE.md §7).
		if (!from.currency().equals(to.currency())) {
			throw new ApiException(HttpStatus.CONFLICT, "currency-mismatch",
					"Transfers between accounts in different currencies aren't supported");
		}
		this.accountService.adjustBalance(from.id(), request.amount().negate());
		this.accountService.adjustBalance(to.id(), request.amount());
		return TransactionEntity.transfer(request.amount(), request.date(), scope.userId(), request.note(), from.id(),
				to.id());
	}

	private List<TransactionView> toViews(List<TransactionEntity> entities) {
		Set<UUID> accountIds = new HashSet<>();
		Set<UUID> categoryIds = new HashSet<>();
		for (TransactionEntity entity : entities) {
			accountIds.addAll(entity.accountIds());
			if (entity.getCategoryId() != null) {
				categoryIds.add(entity.getCategoryId());
			}
		}
		Map<UUID, AccountRef> accounts = this.accountService.refs(accountIds);
		Map<UUID, CategoryView> categories = this.categoryService.views(categoryIds);
		return entities.stream().map((entity) -> toView(entity, accounts, categories)).toList();
	}

	private static TransactionView toView(TransactionEntity entity, Map<UUID, AccountRef> accounts,
			Map<UUID, CategoryView> categories) {
		AccountRef account = lookup(accounts, entity.getAccountId());
		AccountRef fromAccount = lookup(accounts, entity.getFromAccountId());
		AccountRef toAccount = lookup(accounts, entity.getToAccountId());
		boolean shared = isShared(account) || isShared(fromAccount) || isShared(toAccount);
		return new TransactionView(entity.getId(), entity.getKind(), entity.getAmount(), entity.getOccurredOn(),
				entity.getNote(), entity.getRecordedBy(), account, lookup(categories, entity.getCategoryId()),
				fromAccount, toAccount, shared ? Visibility.SHARED : Visibility.PRIVATE);
	}

	private static <T> @Nullable T lookup(Map<UUID, T> values, @Nullable UUID id) {
		return (id != null) ? values.get(id) : null;
	}

	private static boolean isShared(@Nullable AccountRef account) {
		return account != null && account.visibility() == Visibility.SHARED;
	}

	private static BigDecimal signed(CategoryType type, BigDecimal amount) {
		return (type == CategoryType.INCOME) ? amount : amount.negate();
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "invalid-transaction", message);
	}

}
