package com.equigcc.webhook;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId, Pageable page);

    /** Rows another instance is not already working on; SKIP LOCKED lets several instances share the outbox. */
    @Query(value = "select * from webhook_delivery where status = 'PENDING' and next_attempt_at <= :now "
            + "order by next_attempt_at limit :max for update skip locked", nativeQuery = true)
    List<WebhookDelivery> claimDue(Instant now, int max);
}
