package com.equigcc.repository;

import com.equigcc.domain.VoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VoteRecordRepository extends JpaRepository<VoteRecord, UUID> {

    @Query("select v from VoteRecord v join fetch v.shareholder where v.resolution.id = :resolutionId order by v.castAt")
    List<VoteRecord> findByResolution(@Param("resolutionId") UUID resolutionId);

    boolean existsByResolutionIdAndShareholderId(UUID resolutionId, UUID shareholderId);
}
