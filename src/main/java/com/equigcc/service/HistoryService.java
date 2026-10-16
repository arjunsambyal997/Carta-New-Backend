package com.equigcc.service;

import com.equigcc.domain.InstrumentType;
import com.equigcc.domain.ShareIssuance;
import com.equigcc.repository.ShareIssuanceRepository;
import com.equigcc.repository.ShareholderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who held what, and when: a ledger per shareholder and the family tree of a certificate. */
@Service
public class HistoryService {

    public record LedgerEvent(LocalDate date, String type, String certificateNo, InstrumentType instrumentType, String className, long quantity,
                              String counterpartyEn, String counterpartyAr, String counterpartyCertificateNo, String reason, long sharesAfter) {
    }

    public record Ledger(UUID shareholderId, String nameEn, String nameAr, long currentShares, List<LedgerEvent> events) {
    }

    public record LineageNode(String certificateNo, String status, InstrumentType instrumentType, String holderEn, String holderAr, String className,
                              long quantity, LocalDate dateIssued, String predecessorNo, String reason, int depth, boolean current) {
    }

    private final ShareIssuanceRepository issuances;
    private final ShareholderRepository shareholders;

    public HistoryService(ShareIssuanceRepository issuances, ShareholderRepository shareholders) {
        this.issuances = issuances;
        this.shareholders = shareholders;
    }

    @Transactional(readOnly = true)
    public Ledger ledger(UUID orgId, UUID shareholderId) {
        var holder = shareholders.findByIdAndOrganizationId(shareholderId, orgId)
                .orElseThrow(() -> new NotFoundException("Shareholder " + shareholderId + " not found"));
        return buildLedger(holder.getId(), holder.getFullNameEn(), holder.getFullNameAr(), issuances.findAllByOrganization(orgId));
    }

    Ledger buildLedger(UUID holderId, String nameEn, String nameAr, List<ShareIssuance> all) {
        Map<UUID, ShareIssuance> byId = new HashMap<>();
        Map<UUID, List<ShareIssuance>> successors = new HashMap<>();
        for (ShareIssuance c : all) {
            byId.put(c.getId(), c);
            if (c.getPredecessorId() != null) {
                successors.computeIfAbsent(c.getPredecessorId(), k -> new ArrayList<>()).add(c);
            }
        }
        var retired = CertificateChain.retiredOn(all);

        record Raw(LocalDate date, int order, boolean acquired, ShareIssuance cert, ShareIssuance other, String reason) {
        }
        List<Raw> raw = new ArrayList<>();
        for (ShareIssuance c : all) {
            if (!c.getShareholder().getId().equals(holderId)) {
                continue;
            }
            ShareIssuance pred = c.getPredecessorId() == null ? null : byId.get(c.getPredecessorId());
            raw.add(new Raw(c.getDateIssued(), 0, true, c, pred, CertificateChain.reason(c, pred)));
            LocalDate gone = retired.get(c.getId());
            if (gone != null) {
                var next = successors.getOrDefault(c.getId(), List.of());
                // prefer the successor that went to someone else: that is the counterparty of a transfer
                ShareIssuance other = next.stream().filter(n -> !n.getShareholder().getId().equals(holderId)).findFirst()
                        .orElse(next.isEmpty() ? null : next.getFirst());
                raw.add(new Raw(gone, 1, false, c, other, other == null ? c.getStatus().name() : CertificateChain.reason(other, c)));
            }
        }
        raw.sort(Comparator.comparing(Raw::date).thenComparingInt(Raw::order).thenComparing(r -> r.cert().getCertificateNo()));

        List<LedgerEvent> events = new ArrayList<>();
        long shares = 0;
        for (Raw r : raw) {
            long delta = r.cert().getInstrumentType() == InstrumentType.SHARES ? r.cert().getQuantity() : 0;
            shares += r.acquired() ? delta : -delta;
            var other = r.other();
            boolean differentHolder = other != null && !other.getShareholder().getId().equals(holderId);
            events.add(new LedgerEvent(r.date(), r.acquired() ? "ACQUIRED" : "DISPOSED", r.cert().getCertificateNo(), r.cert().getInstrumentType(),
                    r.cert().getShareClass().getName(), r.cert().getQuantity(),
                    differentHolder ? other.getShareholder().getFullNameEn() : null,
                    differentHolder ? other.getShareholder().getFullNameAr() : null,
                    other == null ? null : other.getCertificateNo(), r.reason(), shares));
        }
        return new Ledger(holderId, nameEn, nameAr, shares, events);
    }

    @Transactional(readOnly = true)
    public List<LineageNode> lineage(UUID orgId, String certificateNo) {
        var all = issuances.findAllByOrganization(orgId);
        var target = all.stream().filter(c -> c.getCertificateNo().equals(certificateNo)).findFirst()
                .orElseThrow(() -> new NotFoundException("Certificate " + certificateNo + " not found"));
        return buildLineage(target, all);
    }

    List<LineageNode> buildLineage(ShareIssuance target, List<ShareIssuance> all) {
        Map<UUID, ShareIssuance> byId = new HashMap<>();
        Map<UUID, List<ShareIssuance>> children = new HashMap<>();
        for (ShareIssuance c : all) {
            byId.put(c.getId(), c);
            if (c.getPredecessorId() != null) {
                children.computeIfAbsent(c.getPredecessorId(), k -> new ArrayList<>()).add(c);
            }
        }
        ShareIssuance root = target;
        for (int guard = 0; root.getPredecessorId() != null && byId.containsKey(root.getPredecessorId()) && guard < 10_000; guard++) {
            root = byId.get(root.getPredecessorId());
        }
        List<LineageNode> out = new ArrayList<>();
        walk(root, null, 0, target, children, byId, out);
        return out;
    }

    private void walk(ShareIssuance c, ShareIssuance parent, int depth, ShareIssuance target, Map<UUID, List<ShareIssuance>> children,
                      Map<UUID, ShareIssuance> byId, List<LineageNode> out) {
        out.add(new LineageNode(c.getCertificateNo(), c.getStatus().name(), c.getInstrumentType(), c.getShareholder().getFullNameEn(),
                c.getShareholder().getFullNameAr(), c.getShareClass().getName(), c.getQuantity(), c.getDateIssued(),
                parent == null ? null : parent.getCertificateNo(), CertificateChain.reason(c, parent), depth, c == target));
        var next = new ArrayList<>(children.getOrDefault(c.getId(), List.of()));
        next.sort(Comparator.comparing(ShareIssuance::getDateIssued).thenComparing(ShareIssuance::getCertificateNo));
        for (ShareIssuance n : next) {
            walk(n, c, depth + 1, target, children, byId, out);
        }
    }
}
