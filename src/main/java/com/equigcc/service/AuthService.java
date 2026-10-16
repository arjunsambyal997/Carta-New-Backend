package com.equigcc.service;

import com.equigcc.domain.AppUser;
import com.equigcc.domain.Currency;
import com.equigcc.domain.Jurisdiction;
import com.equigcc.domain.Membership;
import com.equigcc.domain.Organization;
import com.equigcc.domain.Role;
import com.equigcc.repository.AppUserRepository;
import com.equigcc.repository.MembershipRepository;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.security.JwtService;
import com.equigcc.security.SecretCipher;
import com.equigcc.security.Totp;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Password + TOTP authentication. Tokens are scoped to one organization and one role. A password-only
 * session (no {@code otp} in {@code amr}) can enroll a second factor but cannot read or change organization data.
 */
@Service
public class AuthService {

    private static final Duration PENDING_TTL = Duration.ofMinutes(5);
    private static final String ISSUER = "EquiGCC";

    private final AppUserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final ShareholderRepository shareholders;
    private final SessionService sessions;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SecretCipher cipher;
    private final Clock clock;
    private final Duration accessTtl;
    private final int maxFailed;
    private final Duration lockout;
    private final String dummyHash;

    public AuthService(AppUserRepository users, MembershipRepository memberships, OrganizationRepository organizations,
                       ShareholderRepository shareholders, SessionService sessions, PasswordEncoder encoder, JwtService jwt, SecretCipher cipher, Clock clock,
                       @Value("${equigcc.security.access-token-ttl}") Duration accessTtl,
                       @Value("${equigcc.security.max-failed-logins}") int maxFailed,
                       @Value("${equigcc.security.lockout}") Duration lockout) {
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.shareholders = shareholders;
        this.sessions = sessions;
        this.encoder = encoder;
        this.jwt = jwt;
        this.cipher = cipher;
        this.clock = clock;
        this.accessTtl = accessTtl;
        this.maxFailed = maxFailed;
        this.lockout = lockout;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /**
     * Development and test only: skip the authenticator everywhere (sign-in, enrollment prompts, signing).
     * The app refuses to start in the prod profile with this on, and logs a warning whenever it is on.
     */
    @org.springframework.beans.factory.annotation.Value("${equigcc.dev.skip-mfa:false}")
    private boolean skipMfa;

    @jakarta.annotation.PostConstruct
    void warnIfMfaSkipped() {
        if (skipMfa) {
            org.slf4j.LoggerFactory.getLogger(AuthService.class).warn("equigcc.dev.skip-mfa is ON: authenticator codes are NOT checked. Development only.");
        }
    }

    public record SignupCommand(String email, String password, String displayName, String legalNameEn,
                                String legalNameAr, Jurisdiction jurisdiction, String commercialLicenseNo,
                                Currency baseCurrency, String certificatePrefix) {
    }

    @Transactional
    public AuthResult signup(SignupCommand cmd) {
        requireStrong(cmd.password());
        if (users.findByEmail(cmd.email()).isPresent()) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        if (organizations.existsByCertificatePrefixIgnoreCase(cmd.certificatePrefix())) {
            throw new BusinessRuleException("That certificate prefix is already used by another organization");
        }
        var org = new Organization();
        org.setLegalNameEn(cmd.legalNameEn());
        org.setLegalNameAr(cmd.legalNameAr());
        org.setJurisdiction(cmd.jurisdiction());
        org.setCommercialLicenseNo(cmd.commercialLicenseNo());
        org.setBaseCurrency(cmd.baseCurrency());
        org.setCertificatePrefix(cmd.certificatePrefix().toUpperCase());
        org = organizations.save(org);

        AppUser user = newUser(cmd.email(), cmd.displayName(), cmd.password());
        addMembership(user, org, Role.FOUNDER, null);
        return login(cmd.email(), cmd.password(), org.getId());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult login(String email, String password, UUID organizationId) {
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash); // equalize timing with the real path
            throw new UnauthorizedException("Invalid credentials");
        }
        var now = clock.instant();
        if (user.isLocked(now)) {
            throw new UnauthorizedException("Account temporarily locked. Try again later.");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            registerFailure(user);
            throw new UnauthorizedException("Invalid credentials");
        }
        user.setFailedAttempts(0);

        List<Membership> mine = memberships.findByUser(user.getId());
        if (mine.isEmpty()) {
            throw new UnauthorizedException("This account has no organization access");
        }
        Membership chosen;
        if (organizationId != null) {
            chosen = mine.stream().filter(m -> m.getOrganization().getId().equals(organizationId)).findFirst()
                    .orElseThrow(() -> new UnauthorizedException("No access to that organization"));
        } else if (mine.size() == 1) {
            chosen = mine.getFirst();
        } else {
            return AuthResult.chooseOrg(mine.stream().map(m -> new AuthResult.OrgChoice(
                    m.getOrganization().getId(), m.getOrganization().getLegalNameEn(), m.getRole().name())).toList(),
                    user.getDisplayName());
        }
        UUID orgId = chosen.getOrganization().getId();
        if (skipMfa) {
            return signedIn(user, chosen);
        }
        if (user.isTotpEnabled()) {
            return AuthResult.mfa(jwt.issuePending(user.getEmail(), orgId, PENDING_TTL), orgId,
                    chosen.getRole().name(), user.getDisplayName());
        }
        return AuthResult.ok(accessToken(user, chosen, false, null), null, true, orgId, chosen.getRole().name(), user.getDisplayName());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult verifyMfa(String mfaToken, String code) {
        Claims claims;
        try {
            claims = jwt.parse(mfaToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Verification expired. Sign in again.");
        }
        if (!JwtService.TYPE_MFA_PENDING.equals(claims.get(JwtService.TYPE, String.class))) {
            throw new UnauthorizedException("Verification expired. Sign in again.");
        }
        AppUser user = users.findByEmail(claims.getSubject())
                .orElseThrow(() -> new UnauthorizedException("Verification expired. Sign in again."));
        if (user.isLocked(clock.instant())) {
            throw new UnauthorizedException("Account temporarily locked. Try again later.");
        }
        UUID orgId = UUID.fromString(((List<?>) claims.get(JwtService.ORG_IDS)).getFirst().toString());
        Membership m = memberships.findByUserIdAndOrganizationId(user.getId(), orgId)
                .orElseThrow(() -> new UnauthorizedException("No access to that organization"));
        acceptCode(user, code);
        return signedIn(user, m);
    }

    /** Exchanges a refresh token for a new access token and a new refresh token. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String refreshToken) {
        var opened = sessions.rotate(refreshToken);
        var session = opened.session();
        AppUser user = users.findById(session.getUserId()).orElse(null);
        Membership m = user == null ? null : memberships.findByUserIdAndOrganizationId(user.getId(), session.getOrganizationId()).orElse(null);
        if (user == null || m == null || user.isLocked(clock.instant())) {
            sessions.revoke(session.getId());
            throw new UnauthorizedException("Your session has ended. Sign in again.");
        }
        return AuthResult.ok(accessToken(user, m, session.isMfa(), session.getId()), opened.refreshToken(), false,
                session.getOrganizationId(), m.getRole().name(), user.getDisplayName());
    }

    /** Ends the session an access token belongs to. */
    public void logout(UUID sessionId) {
        if (sessionId != null) {
            sessions.revoke(sessionId);
        }
    }

    /** Re-checks the second factor for an action that needs a fresh confirmation, such as signing. Same replay rules as sign-in. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public void confirmSecondFactor(String email, String code) {
        if (skipMfa) {
            return;
        }
        AppUser user = require(email);
        if (!user.isTotpEnabled()) {
            throw new UnauthorizedException("Set up a second factor first");
        }
        if (user.isLocked(clock.instant())) {
            throw new UnauthorizedException("Account temporarily locked. Try again later.");
        }
        acceptCode(user, code);
    }

    public record Enrollment(String secret, String otpauthUri) {
    }

    @Transactional
    public Enrollment beginEnrollment(String email) {
        AppUser user = require(email);
        if (user.isTotpEnabled()) {
            throw new BusinessRuleException("A second factor is already set up for this account");
        }
        String secret = Totp.newSecret();
        user.setTotpSecretEnc(cipher.encrypt(secret));
        return new Enrollment(secret, Totp.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult confirmEnrollment(String email, UUID orgId, String code) {
        AppUser user = require(email);
        if (user.isTotpEnabled() || user.getTotpSecretEnc() == null) {
            throw new BusinessRuleException("Start enrollment first");
        }
        acceptCode(user, code);
        user.setTotpEnabled(true);
        Membership m = memberships.findByUserIdAndOrganizationId(user.getId(), orgId)
                .orElseThrow(() -> new UnauthorizedException("No access to that organization"));
        return signedIn(user, m);
    }

    public record MemberInvite(String email, String displayName, Role role, UUID shareholderId) {
    }

    public record InviteResult(String email, Role role, String temporaryPassword) {
    }

    /** Adds an existing or new account to the organization. A new account gets a one-time temporary password. */
    @Transactional
    public InviteResult invite(UUID orgId, MemberInvite cmd) {
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        AppUser user = users.findByEmail(cmd.email()).orElse(null);
        String temp = null;
        if (user == null) {
            temp = com.equigcc.security.PasswordGenerator.generate();
            user = newUser(cmd.email(), cmd.displayName(), temp);
        } else if (memberships.findByUserIdAndOrganizationId(user.getId(), orgId).isPresent()) {
            throw new BusinessRuleException("This person is already a member of the organization");
        }
        addMembership(user, org, cmd.role(), cmd.shareholderId());
        return new InviteResult(user.getEmail(), cmd.role(), temp);
    }

    @Transactional(readOnly = true)
    public List<Membership> members(UUID orgId) {
        return memberships.findByOrganization(orgId);
    }

    // ---- internals -------------------------------------------------------------------------

    private void acceptCode(AppUser user, String code) {
        String secret = cipher.decrypt(user.getTotpSecretEnc());
        long step = Totp.matchStep(secret, code, clock.instant(), 1);
        if (step < 0 || step <= user.getTotpLastStep()) {
            registerFailure(user);
            throw new UnauthorizedException("Invalid verification code");
        }
        user.setTotpLastStep(step);
        user.setFailedAttempts(0);
    }

    private void registerFailure(AppUser user) {
        int failed = user.getFailedAttempts() + 1;
        if (failed >= maxFailed) {
            user.setLockedUntil(clock.instant().plus(lockout));
            failed = 0;
        }
        user.setFailedAttempts(failed);
    }

    /** A fully verified sign-in: opens a server-side session and returns its access and refresh tokens. */
    private AuthResult signedIn(AppUser user, Membership m) {
        var opened = sessions.open(user.getId(), m.getOrganization().getId(), true);
        return AuthResult.ok(accessToken(user, m, true, opened.session().getId()), opened.refreshToken(), false,
                m.getOrganization().getId(), m.getRole().name(), user.getDisplayName());
    }

    private String accessToken(AppUser user, Membership m, boolean mfa, UUID sessionId) {
        UUID shareholderId = m.getShareholder() == null ? null : m.getShareholder().getId();
        return jwt.issue(user.getEmail(), List.of(m.getRole().name()), List.of(m.getOrganization().getId()),
                mfa, shareholderId, sessionId, accessTtl);
    }

    private AppUser newUser(String email, String displayName, String password) {
        var u = new AppUser();
        u.setEmail(email.trim());
        u.setDisplayName(displayName);
        u.setPasswordHash(encoder.encode(password));
        return users.save(u);
    }

    private void addMembership(AppUser user, Organization org, Role role, UUID shareholderId) {
        var m = new Membership();
        m.setUser(user);
        m.setOrganization(org);
        m.setRole(role);
        m.setShareholder(shareholderId == null ? null : shareholders.findByIdAndOrganizationId(shareholderId, org.getId())
                .orElseThrow(() -> new NotFoundException("Shareholder " + shareholderId + " not found in organization")));
        memberships.save(m);
    }

    private AppUser require(String email) {
        return users.findByEmail(email).orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
    }

    /** Changes the signed-in user's password after checking the current one; wrong guesses count toward the lockout. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public void changePassword(String email, String current, String next, UUID keepSession) {
        requireStrong(next);
        AppUser user = users.findByEmail(email).orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (user.isLocked(clock.instant())) {
            throw new UnauthorizedException("Account temporarily locked. Try again later.");
        }
        if (!encoder.matches(current, user.getPasswordHash())) {
            registerFailure(user);
            throw new UnauthorizedException("Current password is incorrect");
        }
        user.setFailedAttempts(0);
        user.setPasswordHash(encoder.encode(next));
        sessions.revokeAll(user.getId(), keepSession); // other devices must sign in again
    }

    static void requireStrong(String password) {
        if (password == null || password.length() < 12) {
            throw new BusinessRuleException("Password must be at least 12 characters");
        }
    }
}
