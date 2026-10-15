package com.equigcc.repository;

import com.equigcc.domain.BoardResolution;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardResolutionRepository extends JpaRepository<BoardResolution, UUID> {

    List<BoardResolution> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<BoardResolution> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Serializes votes and status changes on one resolution. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BoardResolution r where r.id = :id and r.organization.id = :orgId")
    Optional<BoardResolution> findForUpdate(@Param("id") UUID id, @Param("orgId") UUID organizationId);
}
