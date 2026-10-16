package com.equigcc.repository;

import com.equigcc.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHash(@Param("hash") String tokenHash);

    @Query("select t from PasswordResetToken t where t.user.id = :userId and t.usedAt is null")
    List<PasswordResetToken> findUnused(@Param("userId") UUID userId);

    @Query("select count(t) from PasswordResetToken t where t.user.id = :userId and t.createdAt > :since")
    long countSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
