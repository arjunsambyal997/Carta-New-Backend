package com.equigcc.approval;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingChangeRepository extends JpaRepository<PendingChange, UUID> {

    List<PendingChange> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    /** Serializes decisions so a change can only be approved or rejected once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingChange p where p.id = :id and p.organization.id = :orgId")
    Optional<PendingChange> findForUpdate(@Param("id") UUID id, @Param("orgId") UUID orgId);
}
