package com.equigcc.web.dto;

import com.equigcc.domain.Currency;
import com.equigcc.domain.Jurisdiction;
import com.equigcc.domain.Organization;

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
