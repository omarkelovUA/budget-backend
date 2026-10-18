package com.familybudget.user.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.user.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	@Query(nativeQuery = true, value = "select exists (select 1 from users where id in :ids"
			+ " and sync_txid >= cast(cast(:since as text) as xid8) and sync_txid < cast(cast(:until as text) as xid8))")
	boolean anyChanged(Collection<UUID> ids, long since, long until);

	Optional<UserEntity> findByEmail(String email);

	Optional<UserEntity> findByGoogleSubject(String googleSubject);

	long countByCreatedAtAfter(Instant after);

	@Modifying
	@Query(nativeQuery = true, value = "insert into user_activity (user_id, day) values (:userId, :day)"
			+ " on conflict do nothing")
	void recordActivity(UUID userId, LocalDate day);

	@Query(nativeQuery = true, value = "select count(distinct user_id) from user_activity where day >= :from")
	long countActiveSince(LocalDate from);

	@Query(nativeQuery = true, value = "select day, count(*) as count from user_activity where day >= :from"
			+ " group by day")
	List<DayCount> activePerDaySince(LocalDate from);

	@Query(nativeQuery = true, value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as count"
			+ " from users where created_at >= :from group by 1")
	List<DayCount> signupsPerDaySince(Instant from);

	interface DayCount {

		LocalDate getDay();

		long getCount();

	}

}
