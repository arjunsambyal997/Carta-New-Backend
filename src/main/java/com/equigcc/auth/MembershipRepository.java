package com.equigcc.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @Query("select m from Membership m join fetch m.organization where m.user.id = :userId")
    List<Membership> findByUser(@Param("userId") UUID userId);

    @Query("select m from Membership m join fetch m.user left join fetch m.shareholder where m.organization.id = :orgId order by m.user.displayName")
    List<Membership> findByOrganization(@Param("orgId") UUID organizationId);

    Optional<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);
}
