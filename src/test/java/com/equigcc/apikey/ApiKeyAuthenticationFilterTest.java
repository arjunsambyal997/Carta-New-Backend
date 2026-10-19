package com.equigcc.apikey;

import com.equigcc.auth.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticationFilterTest {

    private final ApiKeyService keys = mock(ApiKeyService.class);
    private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(keys);
    private final UUID org = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String method, String key, MockFilterChain[] chain) throws Exception {
        var req = new MockHttpServletRequest(method, "/api/v1/organizations/" + org + "/cap-table");
        if (key != null) {
            req.addHeader(ApiKeyAuthenticationFilter.HEADER, key);
        }
        var res = new MockHttpServletResponse();
        chain[0] = new MockFilterChain();
        filter.doFilter(req, res, chain[0]);
        return res;
    }

    @Test
    void requestsWithoutAKeyPassThroughUntouched() throws Exception {
        var chain = new MockFilterChain[1];
        var res = run("GET", null, chain);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain[0].getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validKeyBecomesAReadOnlyAuditorOfItsOrganization() throws Exception {
        when(keys.authenticate("eqk_good")).thenReturn(Optional.of(new ApiKey(org, "k", "eqk_abcd1234", "h", "o")));
        var chain = new MockFilterChain[1];
        run("GET", "eqk_good", chain);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_LEGAL_AUDITOR");
        var user = (AuthenticatedUser) auth.getPrincipal();
        assertThat(user.organizationIds()).containsExactly(org);
        assertThat(user.mfaVerified()).isTrue();
    }

    @Test
    void writesAreRefusedEvenWithAValidKey() throws Exception {
        when(keys.authenticate("eqk_good")).thenReturn(Optional.of(new ApiKey(org, "k", "eqk_abcd1234", "h", "o")));
        var chain = new MockFilterChain[1];
        var res = run("POST", "eqk_good", chain);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain[0].getRequest()).isNull();
    }

    @Test
    void unknownKeysGet401() throws Exception {
        when(keys.authenticate("eqk_bad")).thenReturn(Optional.empty());
        var chain = new MockFilterChain[1];
        var res = run("GET", "eqk_bad", chain);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain[0].getRequest()).isNull();
    }
}
