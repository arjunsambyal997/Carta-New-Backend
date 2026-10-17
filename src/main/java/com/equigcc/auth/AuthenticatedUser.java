package com.equigcc.auth;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Caller identity from the JWT. {@code organizationIds} scopes the caller to the share registers
 * they may touch (a law firm user may hold many); {@code mfaVerified} is true when the token's
 * {@code amr} claim shows a TOTP second factor.
 * {@code sessionId} is the server-side session the token belongs to (null for password-only enrollment tokens).
 * {@code shareholderId} links the account to the shareholder it votes as; null for staff-only accounts.
 */
public record AuthenticatedUser(String subject, Set<UUID> organizationIds, boolean mfaVerified, UUID shareholderId, UUID sessionId) implements Principal {

    public AuthenticatedUser(String subject, Set<UUID> organizationIds, boolean mfaVerified, UUID shareholderId) {
        this(subject, organizationIds, mfaVerified, shareholderId, null);
    }

    @Override
    public String getName() {
        return subject;
    }
}
