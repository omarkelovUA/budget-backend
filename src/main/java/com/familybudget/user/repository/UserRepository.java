package com.familybudget.user.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.user.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	@Query(nativeQuery = true, value = "select exists (select 1 from users where id in :ids"
			+ " and sync_txid >= cast(cast(:since as text) as xid8) and sync_txid < cast(cast(:until as text) as xid8))")
	boolean anyChanged(Collection<UUID> ids, long since, long until);

	Optional<UserEntity> findByEmail(String email);

	Optional<UserEntity> findByGoogleSubject(String googleSubject);

	boolean existsByEmail(String email);

}
