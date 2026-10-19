package com.equigcc.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SystemDtos {

    private SystemDtos() { }

    public record WindowView(UUID id, Instant startsAt, Instant endsAt, String messageEn, String messageAr,
                             boolean readOnly, boolean active) {
        static WindowView of(MaintenanceWindow w, Instant now) {
            return new WindowView(w.getId(), w.getStartsAt(), w.getEndsAt(), w.getMessageEn(), w.getMessageAr(),
                    w.isReadOnly(), w.isActiveAt(now));
        }
    }

    /** What the UI polls: build version plus current and upcoming maintenance. */
    public record StatusView(String version, Instant serverTime, boolean readOnly, List<WindowView> maintenance) { }

    public record WindowRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
                                @NotBlank @Size(max = 500) String messageEn,
                                @NotBlank @Size(max = 500) String messageAr,
                                Boolean readOnly) { }

    public record FlagRequest(boolean enabled) { }
}
