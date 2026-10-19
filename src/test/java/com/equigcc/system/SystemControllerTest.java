package com.equigcc.system;

import com.equigcc.system.SystemDtos.WindowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SystemControllerTest {

    private final SystemService service = mock(SystemService.class);
    private final WindowRequest req = new WindowRequest(Instant.now(), Instant.now().plusSeconds(60), "en", "ar", true);

    @Test
    void adminEndpointsDoNotExistWithoutAConfiguredToken() {
        var c = new SystemController(service, "");
        assertThatThrownBy(() -> c.schedule("anything", req))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void aWrongOrMissingTokenIsRejected() {
        var c = new SystemController(service, "s3cret-token");
        assertThatThrownBy(() -> c.schedule("wrong", req))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        assertThatThrownBy(() -> c.schedule(null, req)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theRightTokenIsAccepted() {
        var c = new SystemController(service, "s3cret-token");
        c.schedule("s3cret-token", req);
    }
}
