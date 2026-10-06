package com.familybudget.family;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, UUID> {

	List<FamilyMemberEntity> findByFamilyIdOrderByJoinedAt(UUID familyId);

	long countByFamilyIdAndRole(UUID familyId, FamilyRole role);

}
