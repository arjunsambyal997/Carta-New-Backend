package com.equigcc.repository;

import com.equigcc.domain.OwnershipLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OwnershipLinkRepository extends JpaRepository<OwnershipLink, UUID> {

    @Query("""
            select l from OwnershipLink l join fetch l.owner join fetch l.owned
             where l.organization.id = :orgId
            """)
    List<OwnershipLink> findByOrganization(@Param("orgId") UUID organizationId);

    @Query("""
            select l from OwnershipLink l join fetch l.owner
             where l.organization.id = :orgId and l.owned.id = :ownedId
            """)
    List<OwnershipLink> findOwnersOf(@Param("orgId") UUID organizationId, @Param("ownedId") UUID ownedId);
}
