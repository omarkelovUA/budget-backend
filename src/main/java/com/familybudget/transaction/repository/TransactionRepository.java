package com.familybudget.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.transaction.entity.TransactionEntity;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

	@Query("select t from TransactionEntity t"
			+ " where t.accountId in :accountIds or t.fromAccountId in :accountIds or t.toAccountId in :accountIds")
	Page<TransactionEntity> findTouching(Collection<UUID> accountIds, Pageable pageable);

	/** Payment totals per (account, category) in a date range: rows of [accountId, categoryId, sum]. */
	@Query("select t.accountId, t.categoryId, sum(t.amount) from TransactionEntity t"
			+ " where t.kind = com.familybudget.transaction.model.TransactionKind.PAYMENT and t.accountId in :accountIds"
			+ " and t.occurredOn >= :from and t.occurredOn < :to group by t.accountId, t.categoryId")
	List<Object[]> sumPaymentsByAccountAndCategory(Collection<UUID> accountIds, LocalDate from, LocalDate to);

	boolean existsByCategoryIdAndRecordedByNot(UUID categoryId, UUID recordedBy);

	@Modifying(flushAutomatically = true)
	@Query("update TransactionEntity t set t.categoryId = :into where t.categoryId = :from")
	int moveCategory(UUID from, UUID into);

	@Query("select coalesce(sum(t.amount), 0) from TransactionEntity t where t.categoryId = :categoryId"
			+ " and t.occurredOn >= :from and t.occurredOn < :to")
	BigDecimal sumPayments(UUID categoryId, LocalDate from, LocalDate to);

	@Query("select coalesce(sum(t.amount), 0) from TransactionEntity t where t.categoryId = :categoryId"
			+ " and t.occurredOn >= :from and t.occurredOn < :to and t.recordedBy = :recordedBy")
	BigDecimal sumPaymentsRecordedBy(UUID categoryId, LocalDate from, LocalDate to, UUID recordedBy);

}
