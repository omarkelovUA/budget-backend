package com.familybudget.family.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.familybudget.family.entity.FamilyMemberEntity;
import com.familybudget.family.model.FamilyRole;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, UUID> {

	List<FamilyMemberEntity> findByFamilyIdOrderByJoinedAt(UUID familyId);

	long countByFamilyId(UUID familyId);

	long countByFamilyIdAndRole(UUID familyId, FamilyRole role);

	/** The admins of every family with more than one member. */
	@Query("""
			select m from FamilyMemberEntity m where m.role = :role and m.familyId in
			    (select o.familyId from FamilyMemberEntity o group by o.familyId having count(o) > 1)""")
	List<FamilyMemberEntity> findInFamiliesWithOthers(FamilyRole role);

}
