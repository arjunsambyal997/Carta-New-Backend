package com.equigcc.organization;

import com.equigcc.captable.Currency;
import com.equigcc.captable.ShareClassType;
import com.equigcc.captable.ShareholderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class SetupDtos {

    private SetupDtos() {
    }

    public record ShareClassRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String nameAr,
            @NotNull ShareClassType classType,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal parValue,
            @NotNull Currency currency,
            /* Votes per share; 0 for non-voting. Defaults to 1 (0 for NON_VOTING_ECONOMIC and TREASURY). */
            @DecimalMin("0") BigDecimal votingMultiplier,
            @DecimalMin("0") BigDecimal dividendPreferenceRatio,
            /* 1.0 = 1x liquidation preference; 0 or empty for common. */
            @DecimalMin("0") BigDecimal liquidationMultiple,
            boolean participating,
            int seniorityRank,
            @Positive Long authorizedShares) {
    }

    public record ShareholderRequest(
            @NotNull ShareholderType holderType,
            @NotBlank @Size(max = 255) String fullNameEn,
            @Size(max = 255) String fullNameAr,
            @Size(max = 32) String passportNo,
            @Size(max = 32) String nationalId,
            /* ISO 3166-1 alpha-2, e.g. AE. */
            @Size(min = 2, max = 2) String nationality,
            @Past java.time.LocalDate dateOfBirth,
            @Size(max = 32) String taxId,
            @Size(max = 64) String registrationNo,
            @Size(max = 1000) String address,
            @Size(max = 255) String email) {
    }
}
