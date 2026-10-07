package com.familybudget.category;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

	@Query("select c from CategoryEntity c where"
			+ " (c.ownerType = com.familybudget.common.OwnerType.USER and c.ownerId = :userId)"
			+ " or (c.ownerType = com.familybudget.common.OwnerType.FAMILY and c.ownerId = :familyId)"
			+ " order by c.type, c.name")
	List<CategoryEntity> findVisible(UUID userId, @Nullable UUID familyId);

	List<CategoryEntity> findByIdIn(Collection<UUID> ids);

	/** A deleted family's categories become its last member's private ones. */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = "update categories set owner_type = 'USER', owner_id = :userId"
			+ " where owner_type = 'FAMILY' and owner_id = :familyId")
	int transferFamilyCategories(UUID familyId, UUID userId);

}
