package com.equigcc.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class OwnershipDtos {

    private OwnershipDtos() {
    }

    public record OwnerInput(
            @NotNull UUID ownerId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal economicPct,
            /* Defaults to the economic percentage when omitted. */
            @DecimalMin("0") @DecimalMax("100") BigDecimal votingPct) {
    }

    public record ReplaceOwnersRequest(@NotNull @Valid List<OwnerInput> owners) {
    }

    public record LinkView(UUID ownerId, String ownerNameEn, String ownerNameAr, UUID ownedId, String ownedNameEn,
                           String ownedNameAr, BigDecimal economicPct, BigDecimal votingPct) {
    }
}
