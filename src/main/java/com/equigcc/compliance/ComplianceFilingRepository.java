package com.equigcc.compliance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplianceFilingRepository extends JpaRepository<ComplianceFiling, UUID> {

    List<ComplianceFiling> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
