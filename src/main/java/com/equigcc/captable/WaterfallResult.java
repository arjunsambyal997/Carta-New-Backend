package com.equigcc.captable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Exit proceeds split by share class and by shareholder. Amounts are in the exit currency, rounded to
 * 2 decimals, so totals can differ from the exit value by a few fils/cents.
 */
public record WaterfallResult(
        UUID organizationId,
        BigDecimal exitValue,
        BigDecimal commonPricePerShare,
        List<ClassPayout> classes,
        List<HolderPayout> holders) {

    public record ClassPayout(
            UUID shareClassId,
            String name,
            long shares,
            BigDecimal invested,
            BigDecimal liquidationPreference,
            BigDecimal preferencePaid,
            /** Non-participating preferred that gave up its preference to share as common. */
            boolean converted,
            BigDecimal residualPaid,
            BigDecimal total,
            BigDecimal pctOfExit) {
    }

    public record HolderPayout(
            UUID shareholderId,
            String nameEn,
            String nameAr,
            BigDecimal total,
            BigDecimal pctOfExit) {
    }
}
