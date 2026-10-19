package com.equigcc.system;

import com.equigcc.common.BusinessRuleException;
import com.equigcc.system.SystemDtos.WindowRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private final MaintenanceWindowRepository windows = mock(MaintenanceWindowRepository.class);
    private final FeatureFlagRepository flags = mock(FeatureFlagRepository.class);
    private final SystemService service =
            new SystemService(windows, flags, Clock.fixed(NOW, ZoneOffset.UTC), "1.2.3");

    private MaintenanceWindow window(Duration startsIn, Duration lasts, boolean readOnly) {
        return new MaintenanceWindow(NOW.plus(startsIn), NOW.plus(startsIn).plus(lasts), "en", "ar", readOnly);
    }

    @Test
    void anActiveReadOnlyWindowSwitchesReadOnlyOn() {
        when(windows.findByEndsAtAfterOrderByStartsAtAsc(NOW))
                .thenReturn(List.of(window(Duration.ofMinutes(-5), Duration.ofMinutes(30), true)));
        var status = service.status();
        assertThat(status.readOnly()).isTrue();
        assertThat(status.maintenance().get(0).active()).isTrue();
        assertThat(status.version()).isEqualTo("1.2.3");
    }

    @Test
    void anUpcomingWindowIsAnnouncedButDoesNotBlockWrites() {
        when(windows.findByEndsAtAfterOrderByStartsAtAsc(NOW))
                .thenReturn(List.of(window(Duration.ofDays(3), Duration.ofHours(1), true)));
        var status = service.status();
        assertThat(status.readOnly()).isFalse();
        assertThat(status.maintenance()).hasSize(1);
    }

    @Test
    void aWindowBeyondTheAnnouncementHorizonIsHidden() {
        when(windows.findByEndsAtAfterOrderByStartsAtAsc(NOW))
                .thenReturn(List.of(window(Duration.ofDays(30), Duration.ofHours(1), true)));
        assertThat(service.status().maintenance()).isEmpty();
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRejected() {
        var bad = new WindowRequest(NOW.plusSeconds(100), NOW.plusSeconds(50), "en", "ar", true);
        assertThatThrownBy(() -> service.schedule(bad)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void flagKeysAreValidated() {
        assertThatThrownBy(() -> service.setFlag("Bad Key!", true)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void settingAFlagCreatesItWhenMissing() {
        when(flags.findByKey("new-ui")).thenReturn(Optional.empty());
        when(flags.findAll()).thenReturn(List.of(new FeatureFlag("new-ui", true)));
        assertThat(service.setFlag("new-ui", true)).containsEntry("new-ui", true);
        org.mockito.Mockito.verify(flags).save(any(FeatureFlag.class));
    }
}
