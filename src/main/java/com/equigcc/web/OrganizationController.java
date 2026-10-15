package com.equigcc.web;

import com.equigcc.service.RegisterQueryService;
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

    private final RegisterQueryService queries;

    public OrganizationController(RegisterQueryService queries) {
        this.queries = queries;
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
}
