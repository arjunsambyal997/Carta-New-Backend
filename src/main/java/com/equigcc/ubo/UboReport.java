package com.equigcc.ubo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result of looking through holding structures to the natural persons behind the register. Percentages are 0-100. */
public record UboReport(
        UUID organizationId,
        String organizationNameEn,
        String organizationNameAr,
        String commercialLicenseNo,
        Instant asOf,
        BigDecimal thresholdPct,
        String basis,
        List<Person> beneficialOwners,
        List<Person> belowThreshold,
        List<Gap> gaps,
        List<String> warnings,
        String disclaimer) {

    public record Person(
            UUID shareholderId,
            String nameEn,
            String nameAr,
            String nationality,
            String documentNo,
            BigDecimal economicPct,
            BigDecimal votingPct,
            /* ECONOMIC, VOTING or BOTH for beneficial owners; null below the threshold. */
            String qualifiesBy,
            boolean direct,
            List<Path> paths) {
    }

    /** One route from the register to the person, e.g. Register > Holdco Ltd > Person. */
    public record Path(List<String> chain, BigDecimal economicPct, BigDecimal votingPct) {
    }

    /** A corporate holder whose ownership could not be fully traced to natural persons. */
    public record Gap(UUID shareholderId, String nameEn, String nameAr, BigDecimal economicPct, BigDecimal votingPct, String reason) {
    }
}
