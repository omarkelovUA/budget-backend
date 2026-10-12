package com.familybudget.family.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.family.entity.FamilyEntity;

public interface FamilyRepository extends JpaRepository<FamilyEntity, UUID> {

	@Query(nativeQuery = true, value = "select exists (select 1 from families where id = :id"
			+ " and sync_txid >= cast(cast(:since as text) as xid8) and sync_txid < cast(cast(:until as text) as xid8))")
	boolean changed(UUID id, long since, long until);

	/** Serializes invites and joins per family, so concurrent ones can't exceed the size limit. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from FamilyEntity f where f.id = :id")
	Optional<FamilyEntity> findByIdForUpdate(UUID id);

}
