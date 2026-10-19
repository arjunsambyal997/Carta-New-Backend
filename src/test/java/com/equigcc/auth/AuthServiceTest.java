package com.equigcc.auth;

import com.equigcc.captable.ShareholderRepository;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.UnauthorizedException;
import com.equigcc.config.StartupSecretsCheck;
import com.equigcc.organization.Organization;
import com.equigcc.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final String PASSWORD = "correct horse battery";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwt = new JwtService(StartupSecretsCheck.DEV_JWT_SECRET);
    private final SecretCipher cipher = new SecretCipher(StartupSecretsCheck.DEV_ENCRYPTION_KEY);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private AuthService service;
    private AppUser user;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var sessionRepo = mock(com.equigcc.auth.AuthSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(i -> {
            var s = (com.equigcc.auth.AuthSession) i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        service = new AuthService(users, memberships, organizations, mock(ShareholderRepository.class),
                new SessionService(sessionRepo, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(14)), encoder, jwt, cipher, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1), 5, Duration.ofMinutes(15));
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("fatima@alnoor.ae");
        user.setDisplayName("Fatima");
        user.setPasswordHash(encoder.encode(PASSWORD));
        when(users.findByEmail("fatima@alnoor.ae")).thenReturn(Optional.of(user));
        when(memberships.findByUser(user.getId())).thenReturn(List.of(membership(orgId, Role.FOUNDER)));
        when(memberships.findByUserIdAndOrganizationId(user.getId(), orgId)).thenReturn(Optional.of(membership(orgId, Role.FOUNDER)));
    }

    @Test
    void signupRejectsACertificatePrefixAnotherOrganizationUses() {
        when(users.findByEmail("new@x.ae")).thenReturn(Optional.empty());
        when(organizations.existsByCertificatePrefixIgnoreCase("acme")).thenReturn(true);
        var cmd = new AuthService.SignupCommand("new@x.ae", PASSWORD, "New", "Acme LLC", null,
                com.equigcc.organization.Jurisdiction.MAINLAND_UAE, "CN-1", com.equigcc.captable.Currency.AED, "acme");
        assertThatThrownBy(() -> service.signup(cmd)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("prefix");
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() {
        when(users.findByEmail("nobody@x.ae")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("nobody@x.ae", PASSWORD, null))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> service.login("fatima@alnoor.ae", "wrong", null))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid credentials");
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("fatima@alnoor.ae", "wrong", null)).isInstanceOf(UnauthorizedException.class);
        }
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThatThrownBy(() -> service.login("fatima@alnoor.ae", PASSWORD, null))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("locked");
    }

    @Test
    void withoutTotpTheTokenIsPasswordOnlyAndEnrollmentIsRequired() {
        var r = service.login("fatima@alnoor.ae", PASSWORD, null);
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.mfaEnrollmentRequired()).isTrue();
        var claims = jwt.parse(r.accessToken());
        assertThat(claims.get("amr", List.class)).containsExactly("pwd");
        assertThat(claims.get("roles", List.class)).containsExactly("FOUNDER");
    }

    @Test
    void withTotpLoginReturnsAPendingTokenThatIsNotAnAccessToken() {
        enableTotp();
        var r = service.login("fatima@alnoor.ae", PASSWORD, null);
        assertThat(r.status()).isEqualTo("MFA_REQUIRED");
        assertThat(r.accessToken()).isNull();
        assertThat(jwt.parse(r.mfaToken()).get(JwtService.TYPE, String.class)).isEqualTo(JwtService.TYPE_MFA_PENDING);
    }

    @Test
    void correctCodeYieldsAnMfaTokenAndReplayIsRejected() {
        String secret = enableTotp();
        String pending = service.login("fatima@alnoor.ae", PASSWORD, null).mfaToken();
        String code = Totp.code(Totp.decodeBase32(secret), Totp.stepOf(NOW));

        var ok = service.verifyMfa(pending, code);
        assertThat(ok.status()).isEqualTo("OK");
        assertThat(jwt.parse(ok.accessToken()).get("amr", List.class)).containsExactly("pwd", "otp");

        assertThatThrownBy(() -> service.verifyMfa(pending, code))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid verification code");
    }

    @Test
    void wrongCodeIsRejectedAndCounted() {
        enableTotp();
        String pending = service.login("fatima@alnoor.ae", PASSWORD, null).mfaToken();
        assertThatThrownBy(() -> service.verifyMfa(pending, "000000")).isInstanceOf(UnauthorizedException.class);
        assertThat(user.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void anAccessTokenCannotBeUsedAsAnMfaToken() {
        String access = service.login("fatima@alnoor.ae", PASSWORD, null).accessToken();
        assertThatThrownBy(() -> service.verifyMfa(access, "123456")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void userInSeveralOrganizationsMustChooseOne() {
        UUID other = UUID.randomUUID();
        when(memberships.findByUser(user.getId())).thenReturn(List.of(membership(orgId, Role.FOUNDER), membership(other, Role.BOARD_MEMBER)));
        var r = service.login("fatima@alnoor.ae", PASSWORD, null);
        assertThat(r.status()).isEqualTo("ORG_SELECTION_REQUIRED");
        assertThat(r.organizations()).hasSize(2);
        assertThat(r.accessToken()).isNull();
    }

    @Test
    void shortPasswordsAreRejectedAtSignup() {
        assertThatThrownBy(() -> AuthService.requireStrong("short")).isInstanceOf(BusinessRuleException.class);
    }

    private String enableTotp() {
        String secret = Totp.newSecret();
        user.setTotpSecretEnc(cipher.encrypt(secret));
        user.setTotpEnabled(true);
        return secret;
    }

    private Membership membership(UUID org, Role role) {
        var o = new Organization();
        o.setId(org);
        o.setLegalNameEn("Org " + org);
        var m = new Membership();
        m.setUser(user);
        m.setOrganization(o);
        m.setRole(role);
        return m;
    }
}
