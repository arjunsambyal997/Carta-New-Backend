package com.equigcc.web;

import com.equigcc.service.RoundSimulator;
import com.equigcc.service.VestingService;
import com.equigcc.web.dto.CertificateResponse;
import com.equigcc.web.dto.OptionsAndRoundsDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final com.equigcc.service.NoticeService notices;

    public OptionsAndRoundsController(RoundSimulator rounds, VestingService vesting, com.equigcc.service.NoticeService notices,
                                      @Value("${equigcc.certificates.verification-base-url}") String verificationBaseUrl) {
        this.rounds = rounds;
        this.vesting = vesting;
        this.notices = notices;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    @Operation(summary = "What a funding round would do to the cap table. Nothing is saved")
    @PostMapping("/rounds/simulate")
    @PreAuthorize(READ)
    public RoundSimulator.Result simulate(@PathVariable UUID orgId, @Valid @RequestBody OptionsAndRoundsDtos.SimulateRoundRequest r) {
        return rounds.simulate(orgId, new RoundSimulator.Input(r.preMoney(), r.investment(), r.poolPct(), r.investorName()));
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
    public List<CertificateResponse> exercise(@PathVariable UUID orgId, @PathVariable String certificateNo,
                                              @Valid @RequestBody OptionsAndRoundsDtos.ExerciseRequest r, Authentication auth) {
        var issued = vesting.exercise(orgId, certificateNo, r.quantity(), r.shareClassId(), r.date(), auth.getName());
        issued.stream().filter(c -> c.getInstrumentType() == com.equigcc.domain.InstrumentType.SHARES)
                .forEach(c -> notices.certificateIssued(orgId, c.getCertificateNo()));
        return issued.stream().map(c -> CertificateResponse.from(c, verificationBaseUrl)).toList();
    }
}
