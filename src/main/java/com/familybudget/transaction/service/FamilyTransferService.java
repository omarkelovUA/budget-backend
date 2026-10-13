package com.familybudget.transaction.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.familybudget.account.model.AccountRef;
import com.familybudget.account.model.TransientAccountView;
import com.familybudget.account.service.AccountService;
import com.familybudget.category.service.CategoryService;
import com.familybudget.common.exception.ApiException;
import com.familybudget.common.exception.NotFoundException;
import com.familybudget.family.event.FamilyMembershipEnded;
import com.familybudget.family.service.FamilyService;
import com.familybudget.transaction.entity.TransactionEntity;
import com.familybudget.transaction.model.FamilyTransfersView;
import com.familybudget.transaction.model.TransactionView;
import com.familybudget.transaction.repository.TransactionRepository;
import com.familybudget.user.model.UserView;
import com.familybudget.user.service.UserService;

/**
 * Money between family members (ARCHITECTURE §3.2): the sender moves it from their own account
 * into the transient account for that recipient, the recipient collects it into one of
 * theirs, and the sender can take back what hasn't been collected. Every step is a plain
 * transfer, so in-transit money keeps counting for the sender.
 */
@Service
@Transactional(readOnly = true)
public class FamilyTransferService {

	/** The sender's expense category for money still in transit when someone leaves the family. */
	public static final String WRITE_OFF_CATEGORY = "Перекази в сім'ї";

	private final TransactionRepository transactions;

	private final AccountService accountService;

	private final CategoryService categoryService;

	private final FamilyService familyService;

	private final UserService userService;

	FamilyTransferService(TransactionRepository transactions, AccountService accountService,
			CategoryService categoryService, FamilyService familyService, UserService userService) {
		this.transactions = transactions;
		this.accountService = accountService;
		this.categoryService = categoryService;
		this.familyService = familyService;
		this.userService = userService;
	}

	/** What the user has sent that's still waiting, and what's waiting for them. */
	public FamilyTransfersView inTransit(UUID userId) {
		List<TransientAccountView> pending = this.accountService.openTransientsInvolving(userId)
			.stream()
			.filter((transfer) -> transfer.balance().signum() > 0)
			.toList();
		Set<UUID> counterparts = new HashSet<>();
		pending.forEach((transfer) -> counterparts.add(counterpart(transfer, userId)));
		Map<UUID, UserView> users = this.userService.getAll(counterparts)
			.stream()
			.collect(Collectors.toMap(UserView::id, Function.identity()));
		List<FamilyTransfersView.InTransit> outgoing = new ArrayList<>();
		List<FamilyTransfersView.InTransit> incoming = new ArrayList<>();
		for (TransientAccountView transfer : pending) {
			UserView other = users.get(counterpart(transfer, userId));
			FamilyTransfersView.InTransit view = new FamilyTransfersView.InTransit(transfer.id(),
					new FamilyTransfersView.Person(other.id(), other.displayName()), transfer.currency(),
					transfer.balance());
			(transfer.senderId().equals(userId) ? outgoing : incoming).add(view);
		}
		return new FamilyTransfersView(outgoing, incoming);
	}

	@Transactional
	public TransactionView send(UUID userId, UUID fromAccountId, UUID recipientId, BigDecimal amount,
			@Nullable LocalDate date, @Nullable String note) {
		AccountRef from = this.accountService.requireOwnOpen(userId, fromAccountId);
		Optional<UUID> family = this.familyService.currentFamilyId(userId);
		if (recipientId.equals(userId) || family.isEmpty()
				|| !family.equals(this.familyService.currentFamilyId(recipientId))) {
			throw new ApiException(HttpStatus.CONFLICT, "recipient-not-in-family",
					"Money can only be sent to another member of your family");
		}
		TransientAccountView transit = this.accountService.openTransient(userId, recipientId, from.currency());
		this.accountService.adjustBalance(from.id(), amount.negate());
		this.accountService.adjustBalance(transit.id(), amount);
		return record(userId, amount, (date != null) ? date : LocalDate.now(), note, from.id(), transit.id());
	}

	/** The recipient moves money in transit into one of their own accounts. */
	@Transactional
	public TransactionView collect(UUID userId, UUID transferId, UUID toAccountId, @Nullable BigDecimal amount) {
		TransientAccountView transit = this.accountService.requireOpenTransient(transferId);
		if (!transit.recipientId().equals(userId)) {
			throw new NotFoundException("Transfer not found");
		}
		return withdraw(userId, transit, toAccountId, amount);
	}

	/** The sender moves money the recipient hasn't collected back into one of their own accounts. */
	@Transactional
	public TransactionView takeBack(UUID userId, UUID transferId, UUID toAccountId, @Nullable BigDecimal amount) {
		TransientAccountView transit = this.accountService.requireOpenTransient(transferId);
		if (!transit.senderId().equals(userId)) {
			throw new NotFoundException("Transfer not found");
		}
		return withdraw(userId, transit, toAccountId, amount);
	}

	/**
	 * Leaving the family ends every transfer with its members: money still in transit is
	 * written off as the sender's expense, then the transient accounts close (ARCHITECTURE §3.2).
	 */
	@EventListener
	@Transactional
	void on(FamilyMembershipEnded event) {
		for (TransientAccountView transit : this.accountService.openTransientsInvolving(event.userId())) {
			BigDecimal left = transit.balance();
			if (left.signum() > 0) {
				UUID category = this.categoryService.systemExpenseCategory(transit.senderId(), WRITE_OFF_CATEGORY).id();
				this.accountService.adjustBalance(transit.id(), left.negate());
				this.transactions.save(TransactionEntity.payment(left, LocalDate.now(), transit.senderId(), null,
						transit.id(), category));
			}
			this.accountService.markClosed(transit.id());
		}
	}

	private TransactionView withdraw(UUID userId, TransientAccountView transit, UUID toAccountId,
			@Nullable BigDecimal amount) {
		AccountRef to = this.accountService.requireOwnOpen(userId, toAccountId);
		if (!to.currency().equals(transit.currency())) {
			throw new ApiException(HttpStatus.CONFLICT, "currency-mismatch",
					"Pick an account in " + transit.currency());
		}
		BigDecimal taken = (amount != null) ? amount : transit.balance();
		if (taken.signum() <= 0) {
			throw new ApiException(HttpStatus.CONFLICT, "insufficient-in-transit", "Nothing is waiting in this transfer");
		}
		this.accountService.withdrawFromTransient(transit.id(), taken);
		this.accountService.adjustBalance(to.id(), taken);
		return record(userId, taken, LocalDate.now(), null, transit.id(), to.id());
	}

	private TransactionView record(UUID userId, BigDecimal amount, LocalDate date, @Nullable String note, UUID from,
			UUID to) {
		TransactionEntity transfer = this.transactions
			.save(TransactionEntity.transfer(amount, date, userId, note, from, to));
		return new TransactionView(transfer.getId(), transfer.getKind(), transfer.getAmount(), transfer.getOccurredOn(),
				transfer.getNote(), userId, null, null, this.accountService.refs(userId, List.of(from)).get(from),
				this.accountService.refs(userId, List.of(to)).get(to), false);
	}

	private static UUID counterpart(TransientAccountView transfer, UUID userId) {
		return transfer.senderId().equals(userId) ? transfer.recipientId() : transfer.senderId();
	}

}
