package com.equigcc.web;

import com.equigcc.security.JwtService;
import com.equigcc.security.OrgAccess;
import com.equigcc.security.SecurityConfig;
import com.equigcc.service.DocumentsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentsController.class)
@Import({SecurityConfig.class, JwtService.class, OrgAccess.class})
class DocumentsControllerTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID RES = UUID.randomUUID();
    private static final UUID MINE = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final String BASE = "/api/v1/organizations/{orgId}/documents";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean DocumentsService documents;

    private String token(String role, UUID shareholderId, boolean mfa) {
        return jwt.issue("user-1", List.of(role), List.of(ORG), mfa, shareholderId, Duration.ofMinutes(5));
    }

    @Test
    void secretaryDownloadsMinutesAsAnAttachment() throws Exception {
        when(documents.minutes(ORG, RES, "user-1")).thenReturn(new DocumentsService.Pdf(new byte[]{'%', 'P', 'D', 'F'}, "minutes-x.pdf"));
        mvc.perform(get(BASE + "/minutes/{id}", ORG, RES).header("Authorization", "Bearer " + token("CORPORATE_SECRETARY", null, true)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"minutes-x.pdf\""));
    }

    @Test
    void shareholdersCannotGetMinutesOrTransferForms() throws Exception {
        mvc.perform(get(BASE + "/minutes/{id}", ORG, RES).header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/transfer-form/ACME-1", ORG).header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true))).andExpect(status().isForbidden());
        verify(documents, never()).minutes(any(), any(), any());
    }

    @Test
    void shareholderGetsOnlyTheirOwnStatement() throws Exception {
        when(documents.statement(ORG, MINE, null, "user-1")).thenReturn(new DocumentsService.Pdf(new byte[]{'%'}, "statement.pdf"));
        mvc.perform(get(BASE + "/statement/{id}", ORG, MINE).header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/statement/{id}", ORG, OTHER).header("Authorization", "Bearer " + token("SHAREHOLDER", MINE, true))).andExpect(status().isNotFound());
        verify(documents, never()).statement(any(), org.mockito.ArgumentMatchers.eq(OTHER), any(), any());
    }

    @Test
    void staffCanPassAnAsOfDate() throws Exception {
        when(documents.statement(ORG, OTHER, java.time.LocalDate.parse("2025-06-30"), "user-1")).thenReturn(new DocumentsService.Pdf(new byte[]{'%'}, "s.pdf"));
        mvc.perform(get(BASE + "/statement/{id}?asOf=2025-06-30", ORG, OTHER).header("Authorization", "Bearer " + token("LEGAL_AUDITOR", null, true))).andExpect(status().isOk());
    }

    @Test
    void mfaIsRequired() throws Exception {
        mvc.perform(get(BASE + "/minutes/{id}", ORG, RES).header("Authorization", "Bearer " + token("FOUNDER", null, false))).andExpect(status().isForbidden());
    }
}
