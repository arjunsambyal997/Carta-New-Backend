package com.equigcc.repository;

import com.equigcc.domain.Shareholder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShareholderRepository extends JpaRepository<Shareholder, UUID> {

    Optional<Shareholder> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
