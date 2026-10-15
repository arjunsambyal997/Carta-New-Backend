package com.equigcc.repository;

import com.equigcc.domain.SuccessionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuccessionRuleRepository extends JpaRepository<SuccessionRule, UUID> {

    @Query("""
            select r from SuccessionRule r
              join fetch r.fromClass join fetch r.toClass
              left join fetch r.subject left join fetch r.beneficiary
             where r.organization.id = :orgId order by r.createdAt desc
            """)
    List<SuccessionRule> findAllByOrganization(@Param("orgId") UUID organizationId);

    @Query("""
            select r from SuccessionRule r
              join fetch r.fromClass join fetch r.toClass
              left join fetch r.subject left join fetch r.beneficiary
             where r.id = :id and r.organization.id = :orgId
            """)
    Optional<SuccessionRule> findByIdAndOrganizationId(@Param("id") UUID id, @Param("orgId") UUID organizationId);

    /** Row lock so two executions of the same rule cannot both proceed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SuccessionRule r where r.id = :id and r.organization.id = :orgId")
    Optional<SuccessionRule> lockByIdAndOrganizationId(@Param("id") UUID id, @Param("orgId") UUID organizationId);
}
