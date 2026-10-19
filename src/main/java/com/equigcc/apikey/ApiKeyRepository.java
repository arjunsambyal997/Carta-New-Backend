package com.equigcc.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    long countByOrganizationIdAndRevokedAtIsNull(UUID organizationId);
}
