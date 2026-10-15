package com.equigcc.web.dto;

import com.equigcc.domain.Currency;
import com.equigcc.domain.InstrumentType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IssueCertificateRequest(
        @NotNull UUID shareholderId,
        @NotNull UUID shareClassId,
        /* Defaults to SHARES. */
        InstrumentType instrumentType,
        @Positive long quantity,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal pricePerShare,
        /* Defaults to the share class currency. */
        Currency currency,
        /* Defaults to today (UTC). */
        LocalDate dateIssued) {
}
