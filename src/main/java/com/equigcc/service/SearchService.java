package com.equigcc.service;

import com.equigcc.domain.ShareIssuance;
import com.equigcc.repository.BoardResolutionRepository;
import com.equigcc.repository.ShareClassRepository;
import com.equigcc.repository.ShareIssuanceRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.repository.VaultDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/** One search box over the register: holders, certificates, resolutions, documents and share classes of one organization. */
@Service
public class SearchService {

    public record Hit(String type, String id, String title, String subtitle) {
    }

    static final int PER_TYPE = 6;

    private final ShareholderRepository shareholders;
    private final ShareIssuanceRepository issuances;
    private final BoardResolutionRepository resolutions;
    private final VaultDocumentRepository vault;
    private final ShareClassRepository classes;

    public SearchService(ShareholderRepository shareholders, ShareIssuanceRepository issuances, BoardResolutionRepository resolutions,
                         VaultDocumentRepository vault, ShareClassRepository classes) {
        this.shareholders = shareholders;
        this.issuances = issuances;
        this.resolutions = resolutions;
        this.vault = vault;
        this.classes = classes;
    }

    @Transactional(readOnly = true)
    public List<Hit> search(UUID orgId, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return List.of();
        }
        var out = new ArrayList<Hit>();
        Predicate<String> m = s -> s != null && s.toLowerCase(Locale.ROOT).contains(q);
        shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId).stream()
                .filter(h -> m.test(h.getFullNameEn()) || m.test(h.getFullNameAr()) || m.test(h.getEmail()) || m.test(h.getRegistrationNo()) || m.test(h.getNationalId()))
                .limit(PER_TYPE).forEach(h -> out.add(new Hit("shareholder", h.getId().toString(), h.getFullNameEn(),
                        h.getFullNameAr() != null ? h.getFullNameAr() : h.getHolderType().name().toLowerCase(Locale.ROOT))));
        issuances.findAllByOrganization(orgId).stream()
                .filter(i -> m.test(i.getCertificateNo()) || m.test(i.getShareholder().getFullNameEn()) || m.test(i.getShareholder().getFullNameAr()))
                .limit(PER_TYPE).forEach((ShareIssuance i) -> out.add(new Hit("certificate", i.getCertificateNo(), i.getCertificateNo(),
                        i.getQuantity() + " " + i.getShareClass().getName() + " · " + i.getShareholder().getFullNameEn())));
        resolutions.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .filter(r -> m.test(r.getTitle()) || m.test(r.getTitleAr()))
                .limit(PER_TYPE).forEach(r -> out.add(new Hit("resolution", r.getId().toString(), r.getTitle(), r.getStatus().name())));
        vault.summaries(orgId).stream().filter(d -> m.test(d.title()) || m.test(d.fileName()))
                .limit(PER_TYPE).forEach(d -> out.add(new Hit("document", d.id().toString(), d.title(), d.category().name())));
        classes.findByOrganizationIdOrderBySeniorityRankDescNameAsc(orgId).stream().filter(c -> m.test(c.getName()) || m.test(c.getNameAr()))
                .limit(PER_TYPE).forEach(c -> out.add(new Hit("shareClass", c.getId().toString(), c.getName(), c.getClassType().name())));
        return out;
    }
}
