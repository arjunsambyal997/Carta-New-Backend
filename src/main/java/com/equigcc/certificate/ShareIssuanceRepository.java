package com.equigcc.certificate;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareIssuanceRepository extends JpaRepository<ShareIssuance, UUID> {

    /** All live rows for an organization, with class and holder fetched to avoid N+1 in the cap table. */
    @Query("""
            select i from ShareIssuance i
              join fetch i.shareClass
              join fetch i.shareholder
             where i.organization.id = :orgId
               and i.status = com.equigcc.certificate.IssuanceStatus.ISSUED
            """)
    List<ShareIssuance> findActiveByOrganization(@Param("orgId") UUID organizationId);

    /** Shares and instruments already allocated against a class's authorized limit. */
    @Query("""
            select coalesce(sum(i.quantity), 0) from ShareIssuance i
             where i.shareClass.id = :classId
               and i.status = com.equigcc.certificate.IssuanceStatus.ISSUED
            """)
    long sumIssuedQuantityByClass(@Param("classId") UUID shareClassId);

    @EntityGraph(attributePaths = {"organization", "shareClass", "shareholder"})
    Optional<ShareIssuance> findByCertificateNo(String certificateNo);

    /** Every certificate of an organization, newest first, including cancelled ones. */
    @Query("""
            select i from ShareIssuance i
              join fetch i.shareClass
              join fetch i.shareholder
              join fetch i.organization
             where i.organization.id = :orgId
             order by i.createdAt desc, i.certificateNo desc
            """)
    List<ShareIssuance> findAllByOrganization(@Param("orgId") UUID organizationId);
}
