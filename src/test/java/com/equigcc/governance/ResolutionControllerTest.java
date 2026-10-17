package com.equigcc.governance;

import com.equigcc.auth.JwtService;
import com.equigcc.auth.OrgAccess;
import com.equigcc.captable.Shareholder;
import com.equigcc.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResolutionController.class)
@Import({SecurityConfig.class, JwtService.class, OrgAccess.class})
class ResolutionControllerTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID RES = UUID.randomUUID();
    private static final UUID MINE = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean ResolutionService service;
    @MockBean com.equigcc.notice.NoticeService notices;

    private static final String URL = "/api/v1/organizations/{orgId}/resolutions/{id}/votes";

    @Test
    void shareholderVotesAsTheirLinkedShareholder() throws Exception {
        when(service.vote(eq(ORG), eq(RES), eq(MINE), eq(VoteChoice.FOR), any())).thenReturn(voteRecord());
        mvc.perform(post(URL, ORG, RES).header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"FOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vote").value("FOR"));
    }

    @Test
    void accountWithoutLinkedShareholderIsForbidden() throws Exception {
        mvc.perform(post(URL, ORG, RES).header("Authorization", "Bearer " + token("SHAREHOLDER", null, true))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"FOR\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).vote(any(), any(), any(), any(), any());
    }

    @Test
    void shareholderCannotVoteOnBehalfOfSomeoneElse() throws Exception {
        mvc.perform(post(URL, ORG, RES).header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"FOR\",\"shareholderId\":\"%s\"}".formatted(OTHER)))
                .andExpect(status().isForbidden());
        verify(service, never()).vote(any(), any(), any(), any(), any());
    }

    @Test
    void corporateSecretaryCanRecordAVoteForAShareholder() throws Exception {
        when(service.vote(eq(ORG), eq(RES), eq(OTHER), eq(VoteChoice.AGAINST), any())).thenReturn(voteRecord());
        mvc.perform(post(URL, ORG, RES).header("Authorization", "Bearer " + token("CORPORATE_SECRETARY", null, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"AGAINST\",\"shareholderId\":\"%s\"}".formatted(OTHER)))
                .andExpect(status().isOk());
    }

    @Test
    void votingRequiresMfa() throws Exception {
        mvc.perform(post(URL, ORG, RES).header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, false))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"FOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shareholderCannotOpenResolutions() throws Exception {
        mvc.perform(post("/api/v1/organizations/{orgId}/resolutions/{id}/open", ORG, RES)
                        .header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createValidatesPayload() throws Exception {
        mvc.perform(post("/api/v1/organizations/{orgId}/resolutions", ORG)
                        .header("Authorization", "Bearer " + token("FOUNDER", null, true))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private String token(String role, UUID shareholderId, boolean mfa) {
        return jwt.issue("user-1", List.of(role), List.of(ORG), mfa, shareholderId, Duration.ofMinutes(5));
    }

    private static VoteRecord voteRecord() {
        var holder = new Shareholder();
        holder.setId(MINE);
        holder.setFullNameEn("Fatima");
        var r = new BoardResolution();
        r.setId(RES);
        var v = new VoteRecord();
        v.setResolution(r);
        v.setShareholder(holder);
        v.setVote(VoteChoice.FOR);
        v.setWeightApplied(BigDecimal.TEN);
        v.setCastAt(Instant.now());
        return v;
    }

    @Test
    void shareholderCannotSendReminders() throws Exception {
        mvc.perform(post("/api/v1/organizations/{orgId}/resolutions/{id}/remind", ORG, RES)
                        .header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true)))
                .andExpect(status().isForbidden());
        verify(service, never()).markReminded(any(), any(), any());
    }
}
