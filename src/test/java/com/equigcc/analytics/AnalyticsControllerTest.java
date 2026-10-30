package com.equigcc.analytics;

import com.equigcc.auth.JwtService;
import com.equigcc.auth.OrgAccess;
import com.equigcc.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, JwtService.class, OrgAccess.class})
class AnalyticsControllerTest {

    private static final UUID ORG = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean AnalyticsEmbedService embeds;

    private String token(String role, UUID org) {
        return "Bearer " + jwt.issue("u@x.ae", List.of(role), List.of(org), true, Duration.ofMinutes(5));
    }

    @Test
    void boardMemberGetsASignedLinkThatIsNeverCached() throws Exception {
        when(embeds.embed(ORG)).thenReturn(Optional.of(new AnalyticsEmbedService.Embed("https://a.example.com/embed/dashboard/t", Instant.EPOCH)));
        mvc.perform(get("/api/v1/organizations/" + ORG + "/analytics/embed").header("Authorization", token("BOARD_MEMBER", ORG)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.url").value("https://a.example.com/embed/dashboard/t"));
    }

    @Test
    void shareholdersAndOtherCompaniesAreRefused() throws Exception {
        mvc.perform(get("/api/v1/organizations/" + ORG + "/analytics/embed").header("Authorization", token("SHAREHOLDER", ORG)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizations/" + ORG + "/analytics/embed").header("Authorization", token("FOUNDER", UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void answers404WhenAnalyticsIsNotSetUp() throws Exception {
        when(embeds.embed(ORG)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/organizations/" + ORG + "/analytics/embed").header("Authorization", token("FOUNDER", ORG)))
                .andExpect(status().isNotFound());
    }
}
