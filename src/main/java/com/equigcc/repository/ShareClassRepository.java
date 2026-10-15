package com.equigcc.repository;

import com.equigcc.domain.ShareClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShareClassRepository extends JpaRepository<ShareClass, UUID> {

    Optional<ShareClass> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
