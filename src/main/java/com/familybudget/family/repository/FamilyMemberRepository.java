package com.familybudget.family.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.familybudget.family.entity.FamilyMemberEntity;
import com.familybudget.family.model.FamilyRole;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, UUID> {

	List<FamilyMemberEntity> findByFamilyIdOrderByJoinedAt(UUID familyId);

	long countByFamilyId(UUID familyId);

	long countByFamilyIdAndRole(UUID familyId, FamilyRole role);

}
