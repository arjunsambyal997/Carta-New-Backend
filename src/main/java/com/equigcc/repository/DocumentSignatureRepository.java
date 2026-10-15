package com.equigcc.repository;

import com.equigcc.domain.DocumentSignature;
import com.equigcc.domain.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentSignatureRepository extends JpaRepository<DocumentSignature, UUID> {

    List<DocumentSignature> findByOrganizationIdAndDocumentTypeAndDocumentIdOrderBySignedAtAsc(UUID organizationId, DocumentType type, UUID documentId);

    boolean existsByOrganizationIdAndDocumentTypeAndDocumentIdAndSignerEmail(UUID organizationId, DocumentType type, UUID documentId, String signerEmail);
}
