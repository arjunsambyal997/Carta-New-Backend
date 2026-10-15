package com.equigcc.service;

import com.equigcc.domain.Currency;
import com.equigcc.domain.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IssueCertificateCommand(
        UUID organizationId,
        UUID shareholderId,
        UUID shareClassId,
        InstrumentType instrumentType,
        long quantity,
        BigDecimal pricePerShare,
        Currency currency,
        LocalDate dateIssued,
        String issuedBy) {
}
