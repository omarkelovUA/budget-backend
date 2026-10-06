package com.familybudget.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update RefreshTokenEntity t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
	int revokeAllForUser(UUID userId, Instant now);

	@Modifying
	@Query("delete from RefreshTokenEntity t where t.expiresAt < :before")
	int deleteExpiredBefore(Instant before);

}
