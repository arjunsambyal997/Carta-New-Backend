package com.equigcc.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<WebhookEndpoint> findByOrganizationIdAndActiveTrue(UUID organizationId);

    long countByOrganizationId(UUID organizationId);
}
