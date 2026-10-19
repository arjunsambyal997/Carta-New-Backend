package com.equigcc.captable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareholderRepository extends JpaRepository<Shareholder, UUID> {

    List<Shareholder> findByOrganizationIdOrderByFullNameEnAsc(UUID organizationId);

    Optional<Shareholder> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
