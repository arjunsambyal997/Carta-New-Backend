package com.equigcc.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Tenant checks used from {@code @PreAuthorize} as {@code @orgAccess}. */
@Component("orgAccess")
public class OrgAccess {

    public boolean canAccess(Authentication auth, UUID organizationId) {
        return auth != null
                && auth.getPrincipal() instanceof AuthenticatedUser user
                && user.organizationIds().contains(organizationId);
    }

    public boolean mfaVerified(Authentication auth) {
        return auth != null
                && auth.getPrincipal() instanceof AuthenticatedUser user
                && user.mfaVerified();
    }
}
