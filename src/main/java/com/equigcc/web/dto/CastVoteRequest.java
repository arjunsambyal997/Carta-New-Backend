package com.equigcc.web.dto;

import com.equigcc.domain.VoteChoice;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code shareholderId} is only honoured for a CORPORATE_SECRETARY recording a vote on a holder's behalf;
 * everyone else votes as the shareholder linked to their account.
 */
public record CastVoteRequest(@NotNull VoteChoice vote, UUID shareholderId) {
}
