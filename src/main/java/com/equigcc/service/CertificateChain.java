package com.equigcc.service;

import com.equigcc.domain.InstrumentType;
import com.equigcc.domain.IssuanceStatus;
import com.equigcc.domain.ShareIssuance;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How certificates replace each other. Transfers, buy-backs, option exercises and succession conversions retire the
 * old certificate and issue new ones that point back to it, so the register keeps a full chain.
 */
final class CertificateChain {

    private CertificateChain() {
    }

    /**
     * The date each retired certificate stopped counting: the date of the earliest certificate that replaced it, or,
     * for a cancellation with no replacement, the day it was changed.
     */
    static Map<UUID, LocalDate> retiredOn(List<ShareIssuance> all) {
        Map<UUID, LocalDate> firstSuccessor = new HashMap<>();
        for (ShareIssuance c : all) {
            if (c.getPredecessorId() != null) {
                firstSuccessor.merge(c.getPredecessorId(), c.getDateIssued(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        Map<UUID, LocalDate> out = new HashMap<>();
        for (ShareIssuance c : all) {
            if (c.getStatus() == IssuanceStatus.ISSUED) {
                continue;
            }
            LocalDate d = firstSuccessor.get(c.getId());
            if (d == null) {
                d = c.getUpdatedAt() == null ? c.getDateIssued() : LocalDate.ofInstant(c.getUpdatedAt(), ZoneOffset.UTC);
            }
            out.put(c.getId(), d.isBefore(c.getDateIssued()) ? c.getDateIssued() : d);
        }
        return out;
    }

    /** Why a certificate exists: a fresh issue, a transfer, an option exercise, a succession conversion, or a reissue to the same holder. */
    static String reason(ShareIssuance c, ShareIssuance predecessor) {
        if (predecessor == null) {
            return "ISSUE";
        }
        if (c.getSuccessionRuleId() != null) {
            return "SUCCESSION";
        }
        if (predecessor.getInstrumentType() != InstrumentType.SHARES && c.getInstrumentType() == InstrumentType.SHARES) {
            return "EXERCISE";
        }
        if (!predecessor.getShareholder().getId().equals(c.getShareholder().getId())) {
            return "TRANSFER";
        }
        return "REISSUE";
    }
}
