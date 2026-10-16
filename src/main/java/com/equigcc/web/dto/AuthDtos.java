package com.equigcc.web.dto;

import com.equigcc.domain.Currency;
import com.equigcc.domain.Jurisdiction;
import com.equigcc.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, UUID organizationId) {
    }

    public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record OrganizationInput(
            @NotBlank @Size(max = 255) String legalNameEn,
            @Size(max = 255) String legalNameAr,
            @NotNull Jurisdiction jurisdiction,
            @NotBlank @Size(max = 64) String commercialLicenseNo,
            @NotNull Currency baseCurrency,
            /* Prefix for certificate numbers, e.g. ACME -> ACME-000001. */
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,12}") String certificatePrefix) {
    }

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 255) String displayName,
            @NotNull @Valid OrganizationInput organization) {
    }

    public record InviteRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 255) String displayName,
            @NotNull Role role,
            UUID shareholderId) {
    }

    public record Me(String email, UUID organizationId, String role, boolean mfaVerified, UUID shareholderId) {
    }

    public record Member(String email, String displayName, Role role, UUID shareholderId, String shareholderName) {
    }
}
