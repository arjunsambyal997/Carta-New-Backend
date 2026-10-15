package com.equigcc.domain;

/** Per-organization roles. The JWT carries exactly one, for the organization the token is scoped to. */
public enum Role {
    FOUNDER, CORPORATE_SECRETARY, BOARD_MEMBER, SHAREHOLDER, LEGAL_AUDITOR
}
