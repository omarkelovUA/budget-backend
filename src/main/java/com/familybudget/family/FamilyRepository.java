package com.familybudget.family;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FamilyRepository extends JpaRepository<FamilyEntity, UUID> {

}
