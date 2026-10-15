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
        String refreshToken,
        String mfaToken,
        boolean mfaEnrollmentRequired,
        UUID organizationId,
        String role,
        String displayName,
        List<OrgChoice> organizations) {

    /** Convenience for callers with no refresh token. */
    public AuthResult(String status, String accessToken, String mfaToken, boolean mfaEnrollmentRequired, UUID organizationId, String role,
                      String displayName, List<OrgChoice> organizations) {
        this(status, accessToken, null, mfaToken, mfaEnrollmentRequired, organizationId, role, displayName, organizations);
    }

    public record OrgChoice(UUID id, String name, String role) {
    }

    static AuthResult ok(String token, String refresh, boolean enrollment, UUID org, String role, String name) {
        return new AuthResult("OK", token, refresh, null, enrollment, org, role, name, null);
    }

    static AuthResult mfa(String pending, UUID org, String role, String name) {
        return new AuthResult("MFA_REQUIRED", null, null, pending, false, org, role, name, null);
    }

    static AuthResult chooseOrg(List<OrgChoice> choices, String name) {
        return new AuthResult("ORG_SELECTION_REQUIRED", null, null, null, false, null, null, name, choices);
    }
}
