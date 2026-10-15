package com.equigcc.repository;

import com.equigcc.domain.VaultCategory;
import com.equigcc.domain.VaultDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultDocumentRepository extends JpaRepository<VaultDocument, UUID> {

    /** Listing row without the file bytes. */
    record Summary(UUID id, String title, VaultCategory category, String fileName, String contentType, long sizeBytes,
                   String sha256, String uploadedBy, Instant createdAt) {
    }

    @Query("""
            select new com.equigcc.repository.VaultDocumentRepository$Summary(d.id, d.title, d.category, d.fileName,
                   d.contentType, d.sizeBytes, d.sha256, d.uploadedBy, d.createdAt)
              from VaultDocument d where d.organization.id = :orgId order by d.createdAt desc
            """)
    List<Summary> summaries(@Param("orgId") UUID organizationId);

    Optional<VaultDocument> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
