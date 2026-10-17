package com.equigcc.certificate;

import com.equigcc.captable.Currency;
import com.equigcc.captable.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        String certificateNo,
        UUID organizationId,
        UUID shareholderId,
        String shareholderNameEn,
        String shareholderNameAr,
        UUID shareClassId,
        String shareClassName,
        InstrumentType instrumentType,
        long quantity,
        BigDecimal pricePerShare,
        Currency currency,
        BigDecimal totalConsideration,
        LocalDate dateIssued,
        IssuanceStatus status,
        String recordHash,
        String verificationUrl) {

    public static CertificateResponse from(ShareIssuance i, String verificationBaseUrl) {
        return new CertificateResponse(
                i.getId(),
                i.getCertificateNo(),
                i.getOrganization().getId(),
                i.getShareholder().getId(),
                i.getShareholder().getFullNameEn(),
                i.getShareholder().getFullNameAr(),
                i.getShareClass().getId(),
                i.getShareClass().getName(),
                i.getInstrumentType(),
                i.getQuantity(),
                i.getPricePerShare(),
                i.getCurrency(),
                i.getPricePerShare().multiply(BigDecimal.valueOf(i.getQuantity())),
                i.getDateIssued(),
                i.getStatus(),
                i.getRecordHash(),
                verificationBaseUrl + i.getCertificateNo() + "?h=" + i.getRecordHash());
    }
}
