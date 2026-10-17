package com.equigcc.captable;

import com.equigcc.common.NotFoundException;
import com.equigcc.organization.OrganizationRepository;
import com.equigcc.organization.OrganizationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Read-only lookups that back the frontend's pickers and headers. */
@Service
@Transactional(readOnly = true)
public class RegisterQueryService {

    private final OrganizationRepository organizations;
    private final ShareClassRepository shareClasses;
    private final ShareholderRepository shareholders;

    public RegisterQueryService(OrganizationRepository organizations, ShareClassRepository shareClasses,
                                ShareholderRepository shareholders) {
        this.organizations = organizations;
        this.shareClasses = shareClasses;
        this.shareholders = shareholders;
    }

    public OrganizationView organization(UUID orgId) {
        return organizations.findById(orgId).map(OrganizationView::from)
                .orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
    }

    public List<ShareClassView> shareClasses(UUID orgId) {
        return shareClasses.findByOrganizationIdOrderBySeniorityRankDescNameAsc(orgId).stream()
                .map(ShareClassView::from).toList();
    }

    public List<ShareholderView> shareholders(UUID orgId) {
        return shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId).stream()
                .map(ShareholderView::from).toList();
    }
}
