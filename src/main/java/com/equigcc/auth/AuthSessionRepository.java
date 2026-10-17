package com.equigcc.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByRefreshHash(String hash);

    Optional<AuthSession> findByPrevRefreshHash(String hash);

    @Query("select count(s) > 0 from AuthSession s where s.id = :id and s.revokedAt is null")
    boolean isActive(@Param("id") UUID id);

    @Query("select s from AuthSession s where s.userId = :userId and s.revokedAt is null")
    List<AuthSession> findActiveByUser(@Param("userId") UUID userId);
}
