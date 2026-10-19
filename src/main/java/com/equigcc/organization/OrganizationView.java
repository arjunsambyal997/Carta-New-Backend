package com.equigcc.organization;

import com.equigcc.captable.Currency;

import java.util.UUID;

public record OrganizationView(
        UUID id,
        String legalNameEn,
        String legalNameAr,
        Jurisdiction jurisdiction,
        String commercialLicenseNo,
        Currency baseCurrency,
        boolean requireApproval,
        boolean hasLogo,
        java.time.Instant logoUpdatedAt) {

    public static OrganizationView from(Organization o) {
        return new OrganizationView(o.getId(), o.getLegalNameEn(), o.getLegalNameAr(), o.getJurisdiction(),
                o.getCommercialLicenseNo(), o.getBaseCurrency(), o.isRequireApproval(),
                o.getLogoUpdatedAt() != null, o.getLogoUpdatedAt());
    }
}
