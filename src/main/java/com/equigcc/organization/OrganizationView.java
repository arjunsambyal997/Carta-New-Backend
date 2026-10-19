package com.equigcc.organization;

import com.equigcc.captable.Currency;

import java.util.UUID;

public record OrganizationView(
        UUID id,
        String legalNameEn,
        String legalNameAr,
        Jurisdiction jurisdiction,
        String commercialLicenseNo,
        Currency baseCurrency) {

    public static OrganizationView from(Organization o) {
        return new OrganizationView(o.getId(), o.getLegalNameEn(), o.getLegalNameAr(), o.getJurisdiction(),
                o.getCommercialLicenseNo(), o.getBaseCurrency());
    }
}
