package com.familybudget.category;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

	@Query("select c from CategoryEntity c where"
			+ " (c.ownerType = com.familybudget.common.OwnerType.USER and c.ownerId = :userId)"
			+ " or (c.ownerType = com.familybudget.common.OwnerType.FAMILY and c.ownerId = :familyId)"
			+ " order by c.type, c.name")
	List<CategoryEntity> findVisible(UUID userId, @Nullable UUID familyId);

	List<CategoryEntity> findByIdIn(Collection<UUID> ids);

}
