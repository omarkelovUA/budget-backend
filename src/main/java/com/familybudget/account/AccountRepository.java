package com.familybudget.account;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

	String VISIBLE = "(a.ownerType = com.familybudget.common.OwnerType.USER and a.ownerId = :userId)"
			+ " or (a.ownerType = com.familybudget.common.OwnerType.FAMILY and a.ownerId = :familyId)";

	@Query("select a from AccountEntity a where " + VISIBLE + " order by a.createdAt")
	List<AccountEntity> findVisible(UUID userId, @Nullable UUID familyId);

	@Query("select a.id from AccountEntity a where " + VISIBLE)
	List<UUID> findVisibleIds(UUID userId, @Nullable UUID familyId);

	/** Atomic, so concurrent transactions on the same account can't lose an update. */
	@Modifying
	@Query("update AccountEntity a set a.balance = a.balance + :delta where a.id = :id")
	int addToBalance(UUID id, BigDecimal delta);

	List<AccountEntity> findByIdIn(Collection<UUID> ids);

}
