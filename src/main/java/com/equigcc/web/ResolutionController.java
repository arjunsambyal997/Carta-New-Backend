package com.equigcc.web;

import com.equigcc.domain.BoardResolution;
import com.equigcc.security.AuthenticatedUser;
import com.equigcc.service.CreateResolutionCommand;
import com.equigcc.service.ResolutionService;
import com.equigcc.web.dto.CastVoteRequest;
import com.equigcc.web.dto.CreateResolutionRequest;
import com.equigcc.web.dto.ResolutionViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/resolutions")
@Tag(name = "Board resolutions")
public class ResolutionController {

    private static final String OFFICER = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') "
            + "and @orgAccess.canAccess(authentication, #orgId) and @orgAccess.mfaVerified(authentication)";
    private static final String VOTER = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','SHAREHOLDER') "
            + "and @orgAccess.canAccess(authentication, #orgId) and @orgAccess.mfaVerified(authentication)";
    private static final String READER = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','SHAREHOLDER','LEGAL_AUDITOR') "
            + "and @orgAccess.canAccess(authentication, #orgId)";

    private final ResolutionService service;

    private final com.equigcc.service.NoticeService notices;

    public ResolutionController(ResolutionService service, com.equigcc.service.NoticeService notices) {
        this.service = service;
        this.notices = notices;
    }

    @PostMapping
    @PreAuthorize(OFFICER)
    public ResponseEntity<ResolutionViews.Resolution> create(@PathVariable UUID orgId,
                                                             @Valid @RequestBody CreateResolutionRequest req,
                                                             Authentication auth) {
        BoardResolution r = service.create(orgId, new CreateResolutionCommand(req.title(), req.titleAr(),
                req.description(), req.threshold(), req.quorumPct(), req.votingDeadline()), auth.getName());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(r.getId()).toUri();
        return ResponseEntity.created(location).body(ResolutionViews.Resolution.from(r, service.tallyOf(r)));
    }

    @GetMapping
    @PreAuthorize(READER)
    public List<ResolutionViews.Resolution> list(@PathVariable UUID orgId) {
        return service.list(orgId).stream().map(r -> ResolutionViews.Resolution.from(r, service.tallyOf(r))).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(READER)
    public ResolutionViews.Detail detail(@PathVariable UUID orgId, @PathVariable UUID id) {
        BoardResolution r = service.get(orgId, id);
        return new ResolutionViews.Detail(ResolutionViews.Resolution.from(r, service.tallyOf(r)),
                service.votesOf(id).stream().map(ResolutionViews.Vote::from).toList());
    }

    @PostMapping("/{id}/open")
    @PreAuthorize(OFFICER)
    public ResolutionViews.Resolution open(@PathVariable UUID orgId, @PathVariable UUID id, Authentication auth) {
        BoardResolution r = service.open(orgId, id, auth.getName());
        notices.resolutionOpened(orgId, id);
        return ResolutionViews.Resolution.from(r, service.tallyOf(r));
    }

    @PostMapping("/{id}/votes")
    @PreAuthorize(VOTER)
    public ResolutionViews.Vote vote(@PathVariable UUID orgId, @PathVariable UUID id,
                                     @Valid @RequestBody CastVoteRequest req, Authentication auth) {
        UUID voter = voterFor(req, auth);
        return ResolutionViews.Vote.from(service.vote(orgId, id, voter, req.vote(), auth.getName()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(OFFICER)
    public ResolutionViews.Resolution close(@PathVariable UUID orgId, @PathVariable UUID id, Authentication auth) {
        BoardResolution r = service.close(orgId, id, auth.getName());
        notices.resolutionClosed(orgId, id);
        return ResolutionViews.Resolution.from(r, service.tallyOf(r));
    }

    /** Emails everyone who has not voted yet. Throttled to once an hour per resolution. */
    @PostMapping("/{id}/remind")
    @PreAuthorize(OFFICER)
    public ResolutionViews.Reminder remind(@PathVariable UUID orgId, @PathVariable UUID id, Authentication auth) {
        if (!notices.enabled()) {
            throw new com.equigcc.service.BusinessRuleException("Email notices are switched off on this server");
        }
        BoardResolution r = service.markReminded(orgId, id, auth.getName());
        return new ResolutionViews.Reminder(notices.remindNonVoters(orgId, id), r.getManualReminderAt());
    }

    private static UUID voterFor(CastVoteRequest req, Authentication auth) {
        if (req.shareholderId() != null) {
            boolean secretary = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CORPORATE_SECRETARY"));
            if (!secretary) {
                throw new AccessDeniedException("Only a corporate secretary can record a vote for another shareholder");
            }
            return req.shareholderId();
        }
        if (auth.getPrincipal() instanceof AuthenticatedUser u && u.shareholderId() != null) {
            return u.shareholderId();
        }
        throw new AccessDeniedException("No shareholder is linked to this account");
    }
}
