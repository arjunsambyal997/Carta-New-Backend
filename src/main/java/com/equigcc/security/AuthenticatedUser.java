package com.equigcc.security;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Caller identity from the JWT. {@code organizationIds} scopes the caller to the share registers
 * they may touch (a law firm user may hold many); {@code mfaVerified} is true when the token's
 * {@code amr} claim shows a TOTP second factor.
 */
public record AuthenticatedUser(String subject, Set<UUID> organizationIds, boolean mfaVerified) implements Principal {

    @Override
    public String getName() {
        return subject;
    }
}
