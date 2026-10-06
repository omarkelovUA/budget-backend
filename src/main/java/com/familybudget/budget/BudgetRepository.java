package com.familybudget.budget;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

	@Query("select b from BudgetEntity b where"
			+ " ((b.ownerType = com.familybudget.common.OwnerType.USER and b.ownerId = :userId)"
			+ " or (b.ownerType = com.familybudget.common.OwnerType.FAMILY and b.ownerId = :familyId))"
			+ " order by b.period desc, b.createdAt")
	List<BudgetEntity> findVisible(UUID userId, @Nullable UUID familyId);

}
