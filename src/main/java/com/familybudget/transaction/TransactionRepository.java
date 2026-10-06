package com.familybudget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

	@Query("select t from TransactionEntity t"
			+ " where t.accountId in :accountIds or t.fromAccountId in :accountIds or t.toAccountId in :accountIds")
	Page<TransactionEntity> findTouching(Collection<UUID> accountIds, Pageable pageable);

	@Query("select coalesce(sum(t.amount), 0) from TransactionEntity t where t.categoryId = :categoryId"
			+ " and t.occurredOn >= :from and t.occurredOn < :to")
	BigDecimal sumPayments(UUID categoryId, LocalDate from, LocalDate to);

	@Query("select coalesce(sum(t.amount), 0) from TransactionEntity t where t.categoryId = :categoryId"
			+ " and t.occurredOn >= :from and t.occurredOn < :to and t.recordedBy = :recordedBy")
	BigDecimal sumPaymentsRecordedBy(UUID categoryId, LocalDate from, LocalDate to, UUID recordedBy);

}
