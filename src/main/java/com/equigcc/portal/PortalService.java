package com.equigcc.portal;

import com.equigcc.captable.CapTableService;
import com.equigcc.captable.ShareholderRepository;
import com.equigcc.certificate.IssuanceStatus;
import com.equigcc.certificate.ShareIssuanceRepository;
import com.equigcc.common.NotFoundException;
import com.equigcc.governance.BoardResolutionRepository;
import com.equigcc.governance.ResolutionStatus;
import com.equigcc.governance.VoteRecordRepository;
import com.equigcc.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a shareholder sees about themselves: their own holdings, certificates and the votes waiting for them. Nothing about other holders. */
@Service
public class PortalService {

    public record Position(long issuedShares, long fullyDilutedShares, BigDecimal ownershipPct, BigDecimal votingPct) {
    }

    public record Cert(String certificateNo, String shareClassName, String instrumentType, long quantity, BigDecimal pricePerShare, String currency,
                       LocalDate dateIssued, IssuanceStatus status, String verificationUrl) {
    }

    public record OpenVote(UUID id, String title, String titleAr, Instant votingDeadline, boolean voted) {
    }

    public record Summary(UUID organizationId, String organizationNameEn, String organizationNameAr, UUID shareholderId, String holderNameEn,
                          String holderNameAr, Position position, List<Cert> certificates, List<OpenVote> openVotes) {
    }

    private final OrganizationRepository organizations;
    private final ShareholderRepository shareholders;
    private final ShareIssuanceRepository issuances;
    private final BoardResolutionRepository resolutions;
    private final VoteRecordRepository votes;
    private final CapTableService capTables;

    public PortalService(OrganizationRepository organizations, ShareholderRepository shareholders, ShareIssuanceRepository issuances,
                         BoardResolutionRepository resolutions, VoteRecordRepository votes, CapTableService capTables) {
        this.organizations = organizations;
        this.shareholders = shareholders;
        this.issuances = issuances;
        this.resolutions = resolutions;
        this.votes = votes;
        this.capTables = capTables;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID orgId, UUID shareholderId, String verificationBaseUrl) {
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        var holder = shareholders.findByIdAndOrganizationId(shareholderId, orgId).orElseThrow(() -> new NotFoundException("Shareholder not found"));
        Position pos = capTables.calculate(orgId).holders().stream().filter(h -> h.shareholderId().equals(shareholderId)).findFirst()
                .map(h -> new Position(h.issuedShares(), h.fullyDilutedShares(), h.fullyDilutedOwnershipPct(), h.votingPct()))
                .orElse(new Position(0, 0, BigDecimal.ZERO, BigDecimal.ZERO));
        List<Cert> certs = issuances.findAllByOrganization(orgId).stream()
                .filter(i -> i.getShareholder().getId().equals(shareholderId))
                .map(i -> new Cert(i.getCertificateNo(), i.getShareClass().getName(), i.getInstrumentType().name(), i.getQuantity(), i.getPricePerShare(),
                        i.getCurrency().name(), i.getDateIssued(), i.getStatus(), verificationBaseUrl + i.getCertificateNo() + "?h=" + i.getRecordHash()))
                .toList();
        List<OpenVote> open = resolutions.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .filter(r -> r.getStatus() == ResolutionStatus.ACTIVE)
                .map(r -> new OpenVote(r.getId(), r.getTitle(), r.getTitleAr(), r.getVotingDeadline(), votes.existsByResolutionIdAndShareholderId(r.getId(), shareholderId)))
                .toList();
        return new Summary(orgId, org.getLegalNameEn(), org.getLegalNameAr(), shareholderId, holder.getFullNameEn(), holder.getFullNameAr(), pos, certs, open);
    }
}
