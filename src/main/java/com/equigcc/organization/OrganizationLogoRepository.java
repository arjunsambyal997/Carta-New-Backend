package com.equigcc.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationLogoRepository extends JpaRepository<OrganizationLogo, UUID> {
}
