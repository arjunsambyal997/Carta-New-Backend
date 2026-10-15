package com.equigcc.service;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a login step. {@code status} is OK (accessToken set), MFA_REQUIRED (mfaToken set) or
 * ORG_SELECTION_REQUIRED (pick one of {@code organizations} and log in again).
 */
public record AuthResult(
        String status,
        String accessToken,
        String mfaToken,
        boolean mfaEnrollmentRequired,
        UUID organizationId,
        String role,
        String displayName,
        List<OrgChoice> organizations) {

    public record OrgChoice(UUID id, String name, String role) {
    }

    static AuthResult ok(String token, boolean enrollment, UUID org, String role, String name) {
        return new AuthResult("OK", token, null, enrollment, org, role, name, null);
    }

    static AuthResult mfa(String pending, UUID org, String role, String name) {
        return new AuthResult("MFA_REQUIRED", null, pending, false, org, role, name, null);
    }

    static AuthResult chooseOrg(List<OrgChoice> choices, String name) {
        return new AuthResult("ORG_SELECTION_REQUIRED", null, null, false, null, null, name, choices);
    }
}
