package com.familybudget.subscription.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.subscription.entity.SubscriptionEntity;
import com.familybudget.subscription.model.SubscriptionSource;
import com.familybudget.subscription.model.SubscriptionState;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

	@Query("""
			select count(s) > 0 from SubscriptionEntity s
			where s.userId in :userIds and s.state in :states and (s.expiresAt is null or s.expiresAt > :now)""")
	boolean anyEntitling(Collection<UUID> userIds, Collection<SubscriptionState> states, Instant now);

	@Query("""
			select distinct s.userId from SubscriptionEntity s
			where s.userId in :userIds and s.state in :states and (s.expiresAt is null or s.expiresAt > :now)""")
	Set<UUID> findEntitling(Collection<UUID> userIds, Collection<SubscriptionState> states, Instant now);

	@Query("""
			select count(distinct s.userId) from SubscriptionEntity s
			where s.source = :source and s.state in :states and (s.expiresAt is null or s.expiresAt > :now)""")
	long countEntitling(SubscriptionSource source, Collection<SubscriptionState> states, Instant now);

	@Query("select s.state, count(s) from SubscriptionEntity s where s.source = :source group by s.state")
	List<Object[]> countByState(SubscriptionSource source);

	List<SubscriptionEntity> findByUserId(UUID userId);

	List<SubscriptionEntity> findBySourceOrderByCreatedAt(SubscriptionSource source);

	List<SubscriptionEntity> findByUserIdAndSource(UUID userId, SubscriptionSource source);

	Optional<SubscriptionEntity> findByPurchaseToken(String purchaseToken);

	List<SubscriptionEntity> findBySourceAndStateNotAndExpiresAtBefore(SubscriptionSource source,
			SubscriptionState state, Instant before);

}
