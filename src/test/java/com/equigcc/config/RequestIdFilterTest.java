package com.equigcc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String idFor(String supplied) throws Exception {
        var req = new MockHttpServletRequest();
        if (supplied != null) req.addHeader(RequestIdFilter.HEADER, supplied);
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void generatesAnIdWhenNoneIsSupplied() throws Exception {
        assertThat(idFor(null)).hasSize(36);
    }

    @Test
    void reusesAHarmlessSuppliedId() throws Exception {
        assertThat(idFor("trace-abc12345")).isEqualTo("trace-abc12345");
    }

    @Test
    void replacesAnUnsafeSuppliedId() throws Exception {
        assertThat(idFor("bad id\r\nX-Evil: 1")).hasSize(36);
    }
}
