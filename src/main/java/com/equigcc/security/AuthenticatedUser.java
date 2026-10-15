package com.equigcc.security;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Caller identity from the JWT. {@code organizationIds} scopes the caller to the share registers
 * they may touch (a law firm user may hold many); {@code mfaVerified} is true when the token's
 * {@code amr} claim shows a TOTP second factor.
 * {@code shareholderId} links the account to the shareholder it votes as; null for staff-only accounts.
 */
public record AuthenticatedUser(String subject, Set<UUID> organizationIds, boolean mfaVerified, UUID shareholderId) implements Principal {

    @Override
    public String getName() {
        return subject;
    }
}
