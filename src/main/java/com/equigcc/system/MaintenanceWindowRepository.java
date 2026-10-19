package com.equigcc.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, UUID> {

    /** Windows that have not finished yet, soonest first. */
    List<MaintenanceWindow> findByEndsAtAfterOrderByStartsAtAsc(Instant now);
}
