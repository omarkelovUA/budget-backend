package com.familybudget.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<UserEntity, UUID> {

	Optional<UserEntity> findByEmail(String email);

	Optional<UserEntity> findByGoogleSubject(String googleSubject);

	boolean existsByEmail(String email);

}
