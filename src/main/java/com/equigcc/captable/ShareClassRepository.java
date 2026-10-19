package com.equigcc.captable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareClassRepository extends JpaRepository<ShareClass, UUID> {

    List<ShareClass> findByOrganizationIdOrderBySeniorityRankDescNameAsc(UUID organizationId);

    Optional<ShareClass> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
