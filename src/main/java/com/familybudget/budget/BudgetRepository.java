package com.familybudget.budget;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

	@Query("select b from BudgetEntity b where"
			+ " ((b.ownerType = com.familybudget.common.OwnerType.USER and b.ownerId = :userId)"
			+ " or (b.ownerType = com.familybudget.common.OwnerType.FAMILY and b.ownerId = :familyId))"
			+ " order by b.period desc, b.createdAt")
	List<BudgetEntity> findVisible(UUID userId, @Nullable UUID familyId);

	/**
	 * A deleted family's budgets become its last member's private ones. Their categories
	 * were the family's too and move the same way, so the unique key can't collide.
	 */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "update budgets set owner_type = 'USER', owner_id = :userId"
			+ " where owner_type = 'FAMILY' and owner_id = :familyId")
	int transferFamilyBudgets(UUID familyId, UUID userId);

}
