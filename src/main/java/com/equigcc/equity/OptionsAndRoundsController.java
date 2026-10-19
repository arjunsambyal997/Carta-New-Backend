package com.equigcc.equity;

import com.equigcc.certificate.CertificateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Funding round simulator, option vesting and option exercise. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@Tag(name = "Rounds and options")
public class OptionsAndRoundsController {

    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and @orgAccess.canAccess(authentication, #orgId)";
    private static final String WRITE = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and @orgAccess.canAccess(authentication, #orgId) and @orgAccess.mfaVerified(authentication)";

    private final RoundSimulator rounds;
    private final VestingService vesting;
    private final String verificationBaseUrl;
    private final com.equigcc.notice.NoticeService notices;
    private final com.equigcc.approval.ApprovalService approvals;

    public OptionsAndRoundsController(RoundSimulator rounds, VestingService vesting, com.equigcc.notice.NoticeService notices, com.equigcc.approval.ApprovalService approvals,
                                      @Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl) {
        this.rounds = rounds;
        this.vesting = vesting;
        this.notices = notices;
        this.approvals = approvals;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Operation(summary = "What a funding round would do to the cap table. Nothing is saved")
    @PostMapping("/rounds/simulate")
    @PreAuthorize(READ)
    public RoundSimulator.Result simulate(@PathVariable UUID orgId, @Valid @RequestBody OptionsAndRoundsDtos.SimulateRoundRequest r) {
        var convertibles = r.convertibles() == null ? java.util.List.<RoundSimulator.Convertible>of() : r.convertibles().stream()
                .map(c -> new RoundSimulator.Convertible(c.name(), c.kind() == null ? "SAFE" : c.kind(), c.amount(), c.interestPct(), c.months(), c.capValuation(), c.discountPct()))
                .toList();
        return rounds.simulate(orgId, new RoundSimulator.Input(r.preMoney(), r.investment(), r.poolPct(), r.investorName(), convertibles));
    }

    @Operation(summary = "Option grants with vested, exercisable and unvested counts on a date (default today)")
    @GetMapping("/vesting")
    @PreAuthorize(READ)
    public List<VestingService.Status> list(@PathVariable UUID orgId, @RequestParam(required = false) LocalDate asOf) {
        return vesting.list(orgId, asOf);
    }

    @Operation(summary = "Set the vesting schedule of an option certificate")
    @PutMapping("/certificates/{certificateNo}/vesting")
    @PreAuthorize(WRITE)
    public VestingService.Status schedule(@PathVariable UUID orgId, @PathVariable String certificateNo,
                                          @Valid @RequestBody OptionsAndRoundsDtos.VestingRequest r, Authentication auth) {
        return vesting.schedule(orgId, certificateNo, r.startDate(), r.cliffMonths(), r.vestingMonths(), auth.getName());
    }

    @Operation(summary = "Exercise vested options into shares; certificates are retired and reissued")
    @PostMapping("/certificates/{certificateNo}/exercise")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public ResponseEntity<Object> exercise(@PathVariable UUID orgId, @PathVariable String certificateNo,
                                           @Valid @RequestBody OptionsAndRoundsDtos.ExerciseRequest r, Authentication auth) {
        if (approvals.needsApproval(orgId, auth.getName())) {
            return ResponseEntity.accepted().body(com.equigcc.approval.ApprovalViews.Change.from(approvals.propose(orgId,
                    com.equigcc.approval.PendingChange.Kind.EXERCISE_OPTIONS, new com.equigcc.approval.ApprovalService.ExercisePayload(certificateNo, r), auth.getName())));
        }
        var issued = vesting.exercise(orgId, certificateNo, r.quantity(), r.shareClassId(), r.date(), auth.getName());
        issued.stream().filter(c -> c.getInstrumentType() == com.equigcc.captable.InstrumentType.SHARES)
                .forEach(c -> notices.certificateIssued(orgId, c.getCertificateNo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(issued.stream().map(c -> CertificateResponse.from(c, verificationBaseUrl)).toList());
    }
}
