package com.equigcc.register;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RegisterToolsDtos {

    private RegisterToolsDtos() {
    }

    public record TransferRequest(
            @NotNull UUID fromShareholderId,
            @NotNull UUID toShareholderId,
            @NotNull UUID shareClassId,
            @Positive long quantity,
            /* Price the buyer's certificate records; defaults to the seller's original price. */
            BigDecimal pricePerShare,
            LocalDate date,
            /* Optional PASSED board resolution that authorises the transfer. */
            UUID resolutionId,
            @Size(max = 200) String reference) {
    }

    public record BuyBackRequest(
            @NotNull UUID fromShareholderId,
            @NotNull UUID shareClassId,
            @Positive long quantity,
            @NotNull BigDecimal pricePerShare,
            LocalDate date,
            UUID resolutionId,
            @Size(max = 200) String reference) {
    }

    public record SearchResponse(String query, List<com.equigcc.captable.SearchService.Hit> hits) {
    }
}
