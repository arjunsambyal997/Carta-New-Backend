package com.equigcc.web.dto;

import com.equigcc.domain.Currency;
import com.equigcc.domain.ShareClass;
import com.equigcc.domain.ShareClassType;

import java.math.BigDecimal;
import java.util.UUID;

public record ShareClassView(
        UUID id,
        String name,
        String nameAr,
        ShareClassType classType,
        BigDecimal parValue,
        Currency currency,
        BigDecimal votingMultiplier,
        BigDecimal liquidationMultiple,
        boolean participating,
        Long authorizedShares) {

    public static ShareClassView from(ShareClass c) {
        return new ShareClassView(c.getId(), c.getName(), c.getNameAr(), c.getClassType(), c.getParValue(),
                c.getCurrency(), c.getVotingMultiplier(), c.getLiquidationMultiple(), c.isParticipating(),
                c.getAuthorizedShares());
    }
}
