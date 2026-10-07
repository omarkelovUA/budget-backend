package com.familybudget.family;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface FamilyRepository extends JpaRepository<FamilyEntity, UUID> {

	/** Serializes invites and joins per family, so concurrent ones can't exceed the size limit. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from FamilyEntity f where f.id = :id")
	Optional<FamilyEntity> findByIdForUpdate(UUID id);

}
