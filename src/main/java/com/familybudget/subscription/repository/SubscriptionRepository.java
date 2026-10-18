package com.familybudget.subscription.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

	List<SubscriptionEntity> findByUserId(UUID userId);

	Optional<SubscriptionEntity> findByPurchaseToken(String purchaseToken);

	List<SubscriptionEntity> findBySourceAndStateNotAndExpiresAtBefore(SubscriptionSource source,
			SubscriptionState state, Instant before);

}
