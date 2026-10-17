package com.equigcc.signature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentSignatureRepository extends JpaRepository<DocumentSignature, UUID> {

    List<DocumentSignature> findByOrganizationIdAndDocumentTypeAndDocumentIdOrderBySignedAtAsc(UUID organizationId, DocumentType type, UUID documentId);

    boolean existsByOrganizationIdAndDocumentTypeAndDocumentIdAndSignerEmail(UUID organizationId, DocumentType type, UUID documentId, String signerEmail);
}
