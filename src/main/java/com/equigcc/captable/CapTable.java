package com.equigcc.captable;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Point-in-time cap table. Percentages are 0-100 with 4 decimal places.
 * Treasury shares are reported separately and excluded from ownership and voting.
 */
public record CapTable(
        UUID organizationId,
        Instant asOf,
        long totalIssuedShares,
        long totalFullyDilutedShares,
        long treasuryShares,
        BigDecimal totalVotingPower,
        List<HolderPosition> holders,
        List<ClassSummary> classes) {

    public record HolderPosition(
            UUID shareholderId,
            String nameEn,
            String nameAr,
            long issuedShares,
            long fullyDilutedShares,
            BigDecimal issuedOwnershipPct,
            BigDecimal fullyDilutedOwnershipPct,
            BigDecimal votingPower,
            BigDecimal votingPct) {
    }

    public record ClassSummary(
            UUID shareClassId,
            String name,
            ShareClassType classType,
            long issuedShares,
            long fullyDilutedShares,
            BigDecimal votingPower,
            BigDecimal votingPct) {
    }
}
