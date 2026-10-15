package com.equigcc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** AES-GCM encrypted base32 TOTP secret; null until enrollment starts. */
    @Column(name = "totp_secret_enc")
    private String totpSecretEnc;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Highest TOTP time step already accepted; blocks replay of a code within its validity window. */
    @Column(name = "totp_last_step", nullable = false)
    private long totpLastStep;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getTotpSecretEnc() { return totpSecretEnc; }
    public void setTotpSecretEnc(String totpSecretEnc) { this.totpSecretEnc = totpSecretEnc; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    public long getTotpLastStep() { return totpLastStep; }
    public void setTotpLastStep(long totpLastStep) { this.totpLastStep = totpLastStep; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}
