package com.familybudget.budget.repository;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.budget.entity.BudgetEntity;

public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

	@Query("select b from BudgetEntity b where"
			+ " ((b.ownerType = com.familybudget.common.model.OwnerType.USER and b.ownerId = :userId)"
			+ " or (b.ownerType = com.familybudget.common.model.OwnerType.FAMILY and b.ownerId = :familyId))"
			+ " order by b.period desc, b.createdAt")
	List<BudgetEntity> findVisible(UUID userId, @Nullable UUID familyId);

	/** Budgets on the category other than {@code userId}'s own personal ones. */
	@Query(nativeQuery = true, value = "select exists (select 1 from budgets where category_id = :categoryId"
			+ " and not (owner_type = 'USER' and owner_id = :userId))")
	boolean existsOthersOnCategory(UUID categoryId, UUID userId);

	/**
	 * A merged category's budgets move to the category it was merged into, unless the owner
	 * already has one there for that month; those are dropped.
	 */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "update budgets b set category_id = :into where category_id = :from"
			+ " and not exists (select 1 from budgets o where o.owner_type = b.owner_type"
			+ " and o.owner_id = b.owner_id and o.category_id = :into and o.period = b.period)")
	int moveCategory(UUID from, UUID into);

	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "delete from budgets where category_id = :categoryId")
	int deleteByCategory(UUID categoryId);

	/**
	 * A deleted family's budgets become its last member's private ones. Their categories
	 * were the family's too and move the same way, so the unique key can't collide.
	 */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "update budgets set owner_type = 'USER', owner_id = :userId"
			+ " where owner_type = 'FAMILY' and owner_id = :familyId")
	int transferFamilyBudgets(UUID familyId, UUID userId);

}
