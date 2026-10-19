package com.equigcc.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    Optional<FeatureFlag> findByKey(String key);
}
