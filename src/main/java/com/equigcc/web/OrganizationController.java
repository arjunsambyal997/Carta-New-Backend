package com.equigcc.web;

import com.equigcc.service.AuthService;
import com.equigcc.service.RegisterQueryService;
import com.equigcc.service.SetupService;
import com.equigcc.web.dto.AuthDtos;
import com.equigcc.web.dto.SetupDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.equigcc.web.dto.OrganizationView;
import com.equigcc.web.dto.ShareClassView;
import com.equigcc.web.dto.ShareholderView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@Tag(name = "Organization")
@PreAuthorize("hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') "
        + "and @orgAccess.canAccess(authentication, #orgId)")
public class OrganizationController {

    private static final String OFFICER = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') "
            + "and @orgAccess.canAccess(authentication, #orgId)";

    private final RegisterQueryService queries;
    private final SetupService setup;
    private final AuthService auth;

    public OrganizationController(RegisterQueryService queries, SetupService setup, AuthService auth) {
        this.queries = queries;
        this.setup = setup;
        this.auth = auth;
    }

    @GetMapping
    public OrganizationView organization(@PathVariable UUID orgId) {
        return queries.organization(orgId);
    }

    @GetMapping("/share-classes")
    public List<ShareClassView> shareClasses(@PathVariable UUID orgId) {
        return queries.shareClasses(orgId);
    }

    @GetMapping("/shareholders")
    public List<ShareholderView> shareholders(@PathVariable UUID orgId) {
        return queries.shareholders(orgId);
    }

    @PostMapping("/share-classes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OFFICER)
    public ShareClassView createShareClass(@PathVariable UUID orgId, @Valid @RequestBody SetupDtos.ShareClassRequest r,
                                           Authentication a) {
        return ShareClassView.from(setup.createShareClass(orgId, r, a.getName()));
    }

    @PostMapping("/shareholders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OFFICER)
    public ShareholderView createShareholder(@PathVariable UUID orgId, @Valid @RequestBody SetupDtos.ShareholderRequest r,
                                             Authentication a) {
        return ShareholderView.from(setup.createShareholder(orgId, r, a.getName()));
    }

    @GetMapping("/members")
    @PreAuthorize("hasRole('FOUNDER') and @orgAccess.canAccess(authentication, #orgId)")
    public List<AuthDtos.Member> members(@PathVariable UUID orgId) {
        return auth.members(orgId).stream().map(m -> new AuthDtos.Member(m.getUser().getEmail(),
                m.getUser().getDisplayName(), m.getRole(),
                m.getShareholder() == null ? null : m.getShareholder().getId(),
                m.getShareholder() == null ? null : m.getShareholder().getFullNameEn())).toList();
    }

    /** Adds a team member; a new account's one-time temporary password is returned once and never stored in clear. */
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FOUNDER') and @orgAccess.canAccess(authentication, #orgId)")
    public AuthService.InviteResult invite(@PathVariable UUID orgId, @Valid @RequestBody AuthDtos.InviteRequest r) {
        return auth.invite(orgId, new AuthService.MemberInvite(r.email(), r.displayName(), r.role(), r.shareholderId()));
    }
}
