package com.equigcc.domain;

/** Lifecycle of a resolution. Only ACTIVE resolutions accept votes. */
public enum ResolutionStatus {
    DRAFT, ACTIVE, PASSED, REJECTED
}
