package com.equigcc.system;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceModeFilterTest {

    private final SystemService system = mock(SystemService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
    private final MaintenanceModeFilter filter = new MaintenanceModeFilter(system, clock);

    private MockHttpServletResponse call(String method, String uri) throws Exception {
        var req = new MockHttpServletRequest(method, uri);
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void writesAreRefusedDuringReadOnlyMaintenance() throws Exception {
        when(system.isReadOnlyNow()).thenReturn(true);
        var res = call("POST", "/api/v1/organizations");
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getHeader("Retry-After")).isEqualTo("300");
    }

    @Test
    void readsAndSystemEndpointsKeepWorking() throws Exception {
        when(system.isReadOnlyNow()).thenReturn(true);
        assertThat(call("GET", "/api/v1/organizations").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/system/maintenance").getStatus()).isEqualTo(200);
    }

    @Test
    void writesPassWhenNoWindowIsActive() throws Exception {
        when(system.isReadOnlyNow()).thenReturn(false);
        assertThat(call("POST", "/api/v1/organizations").getStatus()).isEqualTo(200);
    }

    @Test
    void aFailingStatusLookupNeverLocksClientsOut() throws Exception {
        when(system.isReadOnlyNow()).thenThrow(new IllegalStateException("db down"));
        assertThat(call("POST", "/api/v1/organizations").getStatus()).isEqualTo(200);
    }
}
