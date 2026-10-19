package com.equigcc.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

public final class ApprovalViews {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ApprovalViews() {
    }

    /** {@code pending} is true while a change waits, so a client can tell the 202 answer from a normal result. */
    public record Change(UUID id, boolean pending, PendingChange.Kind kind, PendingChange.Status status, String summary, JsonNode details,
                         String requestedBy, Instant requestedAt, String decidedBy, Instant decidedAt, String decisionNote, String result) {

        public static Change from(PendingChange p) {
            JsonNode details;
            try {
                details = JSON.readTree(p.getPayload());
            } catch (Exception e) {
                details = null;
            }
            return new Change(p.getId(), p.getStatus() == PendingChange.Status.PENDING, p.getKind(), p.getStatus(), p.getSummary(), details,
                    p.getRequestedBy(), p.getCreatedAt(), p.getDecidedBy(), p.getDecidedAt(), p.getDecisionNote(), p.getResult());
        }
    }

    public record Decision(String note) {
    }

    public record Setting(boolean required) {
    }
}
