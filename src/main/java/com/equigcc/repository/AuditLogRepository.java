package com.equigcc.repository;

import com.equigcc.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Optional<AuditLog> findFirstByOrganizationIdOrderByIdDesc(UUID organizationId);

    List<AuditLog> findByOrganizationIdOrderByIdAsc(UUID organizationId);

    List<AuditLog> findByOrganizationIdOrderByIdDesc(UUID organizationId, Pageable page);
}
