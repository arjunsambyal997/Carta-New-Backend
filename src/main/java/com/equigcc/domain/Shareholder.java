package com.equigcc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "shareholder")
public class Shareholder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_type", nullable = false, length = 16)
    private ShareholderType holderType;

    @Column(name = "full_name_en", nullable = false)
    private String fullNameEn;

    @Column(name = "full_name_ar")
    private String fullNameAr;

    /** Individuals: passport number. */
    @Column(name = "passport_no", length = 32)
    private String passportNo;

    /** UAE Emirates ID (784-YYYY-NNNNNNN-C) or KSA national / Iqama ID. */
    @Column(name = "national_id", length = 32)
    private String nationalId;

    /** ISO 3166-1 alpha-2, e.g. AE, SA. For corporates: country of incorporation. */
    @Column(name = "nationality", length = 2)
    private String nationality;

    /** UAE TRN / KSA VAT or ZATCA number. */
    @Column(name = "tax_id", length = 32)
    private String taxId;

    /** Corporates: commercial registration / license number. */
    @Column(name = "registration_no", length = 64)
    private String registrationNo;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "email")
    private String email;

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public ShareholderType getHolderType() { return holderType; }
    public void setHolderType(ShareholderType holderType) { this.holderType = holderType; }
    public String getFullNameEn() { return fullNameEn; }
    public void setFullNameEn(String fullNameEn) { this.fullNameEn = fullNameEn; }
    public String getFullNameAr() { return fullNameAr; }
    public void setFullNameAr(String fullNameAr) { this.fullNameAr = fullNameAr; }
    public String getPassportNo() { return passportNo; }
    public void setPassportNo(String passportNo) { this.passportNo = passportNo; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getRegistrationNo() { return registrationNo; }
    public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
