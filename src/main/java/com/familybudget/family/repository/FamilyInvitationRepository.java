package com.familybudget.family.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.familybudget.family.entity.FamilyInvitationEntity;
import com.familybudget.family.model.InvitationStatus;

public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitationEntity, UUID> {

	Optional<FamilyInvitationEntity> findByFamilyIdAndEmailAndStatus(UUID familyId, String email,
			InvitationStatus status);

	List<FamilyInvitationEntity> findByFamilyIdAndStatusAndExpiresAtAfterOrderByCreatedAt(UUID familyId,
			InvitationStatus status, Instant now);

	List<FamilyInvitationEntity> findByEmailAndStatusAndExpiresAtAfterOrderByCreatedAt(String email,
			InvitationStatus status, Instant now);

	long countByFamilyIdAndStatusAndExpiresAtAfter(UUID familyId, InvitationStatus status, Instant now);

}
