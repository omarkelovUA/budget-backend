package com.familybudget.family.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.familybudget.family.entity.FamilyJoinRequestEntity;
import com.familybudget.family.model.InvitationStatus;

public interface FamilyJoinRequestRepository extends JpaRepository<FamilyJoinRequestEntity, UUID> {

	Optional<FamilyJoinRequestEntity> findByRequesterIdAndStatus(UUID requesterId, InvitationStatus status);

	List<FamilyJoinRequestEntity> findByRequesterIdInAndStatus(Collection<UUID> requesterIds, InvitationStatus status);

	List<FamilyJoinRequestEntity> findByFamilyIdAndStatusAndExpiresAtAfterOrderByCreatedAt(UUID familyId,
			InvitationStatus status, Instant now);

}
