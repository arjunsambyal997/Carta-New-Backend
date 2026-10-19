package com.equigcc.organization;

import com.equigcc.captable.Currency;
import com.equigcc.captable.ShareClass;
import com.equigcc.captable.Shareholder;
import com.equigcc.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organization",
        uniqueConstraints = @UniqueConstraint(name = "uk_org_jurisdiction_license",
                columnNames = {"jurisdiction", "commercial_license_no"}))
public class Organization extends BaseEntity {

    @Column(name = "legal_name_en", nullable = false)
    private String legalNameEn;

    @Column(name = "legal_name_ar")
    private String legalNameAr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Jurisdiction jurisdiction;

    @Column(name = "commercial_license_no", nullable = false, length = 64)
    private String commercialLicenseNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", nullable = false, length = 3)
    private Currency baseCurrency;

    /** Short code used as the certificate number prefix, e.g. "ACME" -> ACME-000001. */
    @Column(name = "certificate_prefix", nullable = false, length = 12)
    private String certificatePrefix;

    /** Monotonic counter for certificate numbers; incremented under a row lock. */
    @Column(name = "last_certificate_seq", nullable = false)
    private long lastCertificateSeq;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShareClass> shareClasses = new ArrayList<>();

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Shareholder> shareholders = new ArrayList<>();

    public String nextCertificateNumber() {
        lastCertificateSeq++;
        return "%s-%06d".formatted(certificatePrefix, lastCertificateSeq);
    }

    public String getLegalNameEn() { return legalNameEn; }
    public void setLegalNameEn(String legalNameEn) { this.legalNameEn = legalNameEn; }
    public String getLegalNameAr() { return legalNameAr; }
    public void setLegalNameAr(String legalNameAr) { this.legalNameAr = legalNameAr; }
    public Jurisdiction getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(Jurisdiction jurisdiction) { this.jurisdiction = jurisdiction; }
    public String getCommercialLicenseNo() { return commercialLicenseNo; }
    public void setCommercialLicenseNo(String commercialLicenseNo) { this.commercialLicenseNo = commercialLicenseNo; }
    public Currency getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(Currency baseCurrency) { this.baseCurrency = baseCurrency; }
    public String getCertificatePrefix() { return certificatePrefix; }
    public void setCertificatePrefix(String certificatePrefix) { this.certificatePrefix = certificatePrefix; }
    public long getLastCertificateSeq() { return lastCertificateSeq; }
    public List<ShareClass> getShareClasses() { return shareClasses; }
    public List<Shareholder> getShareholders() { return shareholders; }
}
