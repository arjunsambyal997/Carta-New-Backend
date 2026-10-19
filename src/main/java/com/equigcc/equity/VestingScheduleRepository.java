package com.equigcc.equity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VestingScheduleRepository extends JpaRepository<VestingSchedule, UUID> {

    @Query("select v from VestingSchedule v join fetch v.certificate c join fetch c.shareholder join fetch c.shareClass where v.organization.id = :orgId order by v.startDate, c.certificateNo")
    List<VestingSchedule> findByOrganization(@Param("orgId") UUID orgId);

    @Query("select v from VestingSchedule v join fetch v.certificate c where v.certificate.id = :certificateId and v.organization.id = :orgId")
    Optional<VestingSchedule> findByCertificate(@Param("certificateId") UUID certificateId, @Param("orgId") UUID orgId);
}
