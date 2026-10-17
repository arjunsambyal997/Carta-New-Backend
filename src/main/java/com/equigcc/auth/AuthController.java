package com.equigcc.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService auth;

    private final com.equigcc.auth.PasswordResetService reset;

    public AuthController(AuthService auth, com.equigcc.auth.PasswordResetService reset) {
        this.auth = auth;
        this.reset = reset;
    }

    /** Creates an organization and its first FOUNDER account, then signs in. */
    @PostMapping("/signup")
    public AuthResult signup(@Valid @RequestBody AuthDtos.SignupRequest r) {
        var o = r.organization();
        return auth.signup(new AuthService.SignupCommand(r.email(), r.password(), r.displayName(), o.legalNameEn(),
                o.legalNameAr(), o.jurisdiction(), o.commercialLicenseNo(), o.baseCurrency(), o.certificatePrefix()));
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody AuthDtos.LoginRequest r) {
        return auth.login(r.email(), r.password(), r.organizationId());
    }

    @PostMapping("/mfa/verify")
    public AuthResult verify(@Valid @RequestBody AuthDtos.MfaVerifyRequest r) {
        return auth.verifyMfa(r.mfaToken(), r.code());
    }

    /** Trades a refresh token for a new access token and a new refresh token (the old one stops working). */
    @PostMapping("/refresh")
    public AuthResult refresh(@Valid @RequestBody AuthDtos.RefreshRequest r) {
        return auth.refresh(r.refreshToken());
    }

    /** Ends this session on the server; the access token stops working immediately. */
    @PostMapping("/logout")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(Authentication a) {
        auth.logout(((AuthenticatedUser) a.getPrincipal()).sessionId());
    }

    /** Starts TOTP enrollment for the signed-in account; returns the seed and an otpauth:// URI for a QR code. */
    @PostMapping("/mfa/enroll")
    public AuthService.Enrollment enroll(Authentication a) {
        return auth.beginEnrollment(a.getName());
    }

    @PostMapping("/mfa/confirm")
    public AuthResult confirm(@Valid @RequestBody AuthDtos.CodeRequest r, Authentication a) {
        var me = (AuthenticatedUser) a.getPrincipal();
        return auth.confirmEnrollment(a.getName(), me.organizationIds().iterator().next(), r.code());
    }

    /** Always answers 202, whether or not the address has an account. */
    @PostMapping("/password/forgot")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody AuthDtos.ForgotPasswordRequest r) {
        reset.request(r.email());
    }

    @PostMapping("/password/reset")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody AuthDtos.ResetPasswordRequest r) {
        reset.reset(r.token(), r.newPassword());
    }

    @PostMapping("/password/change")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void change(@Valid @RequestBody AuthDtos.ChangePasswordRequest r, Authentication a) {
        auth.changePassword(a.getName(), r.currentPassword(), r.newPassword(), ((AuthenticatedUser) a.getPrincipal()).sessionId());
    }

    @GetMapping("/me")
    public AuthDtos.Me me(Authentication a) {
        var me = (AuthenticatedUser) a.getPrincipal();
        String role = a.getAuthorities().stream().findFirst().map(g -> g.getAuthority().replace("ROLE_", "")).orElse("");
        return new AuthDtos.Me(me.subject(), me.organizationIds().iterator().next(), role, me.mfaVerified(), me.shareholderId());
    }
}
