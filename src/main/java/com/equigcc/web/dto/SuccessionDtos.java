package com.equigcc.web.dto;

import com.equigcc.domain.SuccessionRule;
import com.equigcc.domain.SuccessionStatus;
import com.equigcc.domain.SuccessionTrigger;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class SuccessionDtos {

    private SuccessionDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull SuccessionTrigger trigger,
            /* Convert only this holder's shares; omit to convert every holder of the source class. */
            UUID subjectId,
            @NotNull UUID fromClassId,
            @NotNull UUID toClassId,
            /* New shares per ratioDenominator shares given up. */
            @Positive long ratioNumerator,
            @Positive long ratioDenominator,
            /* Shares move to this holder; omit to keep them with the current holder. */
            UUID beneficiaryId,
            @NotBlank @Size(max = 500) String legalReference) {
    }

    public record ExecuteRequest(/* Optional: a PASSED board resolution that authorises this execution. */ UUID resolutionId) {
    }

    public record NamedRef(UUID id, String nameEn, String nameAr) {
    }

    public record RuleView(UUID id, String name, SuccessionTrigger trigger, NamedRef subject, NamedRef fromClass, NamedRef toClass,
                           long ratioNumerator, long ratioDenominator, NamedRef beneficiary, String legalReference,
                           SuccessionStatus status, UUID resolutionId, Instant executedAt, String executedBy, String createdBy) {

        public static RuleView from(SuccessionRule r) {
            return new RuleView(r.getId(), r.getName(), r.getTriggerEvent(),
                    r.getSubject() == null ? null : new NamedRef(r.getSubject().getId(), r.getSubject().getFullNameEn(), r.getSubject().getFullNameAr()),
                    new NamedRef(r.getFromClass().getId(), r.getFromClass().getName(), r.getFromClass().getNameAr()),
                    new NamedRef(r.getToClass().getId(), r.getToClass().getName(), r.getToClass().getNameAr()),
                    r.getRatioNumerator(), r.getRatioDenominator(),
                    r.getBeneficiary() == null ? null : new NamedRef(r.getBeneficiary().getId(), r.getBeneficiary().getFullNameEn(), r.getBeneficiary().getFullNameAr()),
                    r.getLegalReference(), r.getStatus(), r.getResolutionId(), r.getExecutedAt(), r.getExecutedBy(), r.getCreatedBy());
        }
    }
}
