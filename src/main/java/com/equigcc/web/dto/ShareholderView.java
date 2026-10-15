package com.equigcc.web.dto;

import com.equigcc.domain.Shareholder;
import com.equigcc.domain.ShareholderType;

import java.util.UUID;

/** List view for pickers; identity documents (passport, national ID, tax ID) are deliberately omitted. */
public record ShareholderView(
        UUID id,
        ShareholderType holderType,
        String fullNameEn,
        String fullNameAr,
        String nationality) {

    public static ShareholderView from(Shareholder s) {
        return new ShareholderView(s.getId(), s.getHolderType(), s.getFullNameEn(), s.getFullNameAr(),
                s.getNationality());
    }
}
