package com.equigcc.system;

import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.system.SystemDtos.StatusView;
import com.equigcc.system.SystemDtos.WindowRequest;
import com.equigcc.system.SystemDtos.WindowView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SystemService {

    static final Duration ANNOUNCE_AHEAD = Duration.ofDays(14);
    private static final Pattern FLAG_KEY = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    private final MaintenanceWindowRepository windows;
    private final FeatureFlagRepository flags;
    private final Clock clock;
    private final String version;

    public SystemService(MaintenanceWindowRepository windows, FeatureFlagRepository flags, Clock clock,
                         @Value("${equigcc.system.version:dev}") String version) {
        this.windows = windows;
        this.flags = flags;
        this.clock = clock;
        this.version = version;
    }

    @Transactional(readOnly = true)
    public StatusView status() {
        Instant now = clock.instant();
        List<WindowView> upcoming = windows.findByEndsAtAfterOrderByStartsAtAsc(now).stream()
                .filter(w -> w.isActiveAt(now) || w.getStartsAt().isBefore(now.plus(ANNOUNCE_AHEAD)))
                .map(w -> WindowView.of(w, now))
                .toList();
        boolean readOnly = upcoming.stream().anyMatch(w -> w.active() && w.readOnly());
        return new StatusView(version, now, readOnly, upcoming);
    }

    @Transactional(readOnly = true)
    public boolean isReadOnlyNow() {
        return status().readOnly();
    }

    @Transactional
    public WindowView schedule(WindowRequest req) {
        Instant now = clock.instant();
        if (!req.endsAt().isAfter(req.startsAt())) throw new BusinessRuleException("The window must end after it starts");
        if (!req.endsAt().isAfter(now)) throw new BusinessRuleException("The window is already over");
        MaintenanceWindow w = windows.save(new MaintenanceWindow(req.startsAt(), req.endsAt(),
                req.messageEn().trim(), req.messageAr().trim(), req.readOnly() == null || req.readOnly()));
        return WindowView.of(w, now);
    }

    @Transactional
    public void cancel(UUID id) {
        windows.delete(windows.findById(id).orElseThrow(() -> new NotFoundException("No such maintenance window")));
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> flags() {
        Map<String, Boolean> out = new TreeMap<>();
        flags.findAll().forEach(f -> out.put(f.getKey(), f.isEnabled()));
        return out;
    }

    @Transactional
    public Map<String, Boolean> setFlag(String key, boolean enabled) {
        if (!FLAG_KEY.matcher(key).matches()) {
            throw new BusinessRuleException("Flag keys are lowercase letters, digits, dots, dashes or underscores");
        }
        FeatureFlag f = flags.findByKey(key).orElseGet(() -> new FeatureFlag(key, enabled));
        f.setEnabled(enabled);
        flags.save(f);
        return flags();
    }
}
