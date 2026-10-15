package com.equigcc.web;

import com.equigcc.security.JwtService;
import com.equigcc.security.OrgAccess;
import com.equigcc.security.SecurityConfig;
import com.equigcc.service.OwnershipService;
import com.equigcc.service.UboService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UboController.class)
@Import({SecurityConfig.class, JwtService.class, OrgAccess.class})
class UboControllerTest {

    private static final UUID ORG = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean OwnershipService ownership;
    @MockBean UboService ubo;

    private String token(String role, boolean mfa) {
        return "Bearer " + jwt.issue("u@x.ae", List.of(role), List.of(ORG), mfa, Duration.ofMinutes(5));
    }

    @Test
    void auditorCanExportButNotEditOwners() throws Exception {
        when(ubo.export(eq(ORG), any(), eq("csv"), any())).thenReturn(new UboService.Export("a,b".getBytes(), "text/csv; charset=utf-8", "ubo-register.csv"));
        mvc.perform(get("/api/v1/organizations/" + ORG + "/ubo/export?format=csv").header("Authorization", token("LEGAL_AUDITOR", true)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ubo-register.csv\""));
        mvc.perform(put("/api/v1/organizations/" + ORG + "/shareholders/" + UUID.randomUUID() + "/owners")
                        .header("Authorization", token("LEGAL_AUDITOR", true)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owners\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shareholdersAndUnverifiedSessionsAreRefused() throws Exception {
        mvc.perform(get("/api/v1/organizations/" + ORG + "/ubo").header("Authorization", token("SHAREHOLDER", true)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizations/" + ORG + "/ubo").header("Authorization", token("FOUNDER", false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretaryCanReplaceOwners() throws Exception {
        when(ownership.replaceOwners(eq(ORG), any(), any(), any())).thenReturn(List.of());
        mvc.perform(put("/api/v1/organizations/" + ORG + "/shareholders/" + UUID.randomUUID() + "/owners")
                        .header("Authorization", token("CORPORATE_SECRETARY", true)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owners\":[{\"ownerId\":\"" + UUID.randomUUID() + "\",\"economicPct\":50}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPercentagesOutOfRange() throws Exception {
        mvc.perform(put("/api/v1/organizations/" + ORG + "/shareholders/" + UUID.randomUUID() + "/owners")
                        .header("Authorization", token("FOUNDER", true)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owners\":[{\"ownerId\":\"" + UUID.randomUUID() + "\",\"economicPct\":101}]}"))
                .andExpect(status().isBadRequest());
    }
}
