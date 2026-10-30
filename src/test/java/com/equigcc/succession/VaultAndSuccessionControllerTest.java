package com.equigcc.succession;

import com.equigcc.auth.JwtService;
import com.equigcc.auth.OrgAccess;
import com.equigcc.config.SecurityConfig;
import com.equigcc.vault.VaultCategory;
import com.equigcc.vault.VaultController;
import com.equigcc.vault.VaultDocumentRepository;
import com.equigcc.vault.VaultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({VaultController.class, SuccessionController.class})
@Import({SecurityConfig.class, JwtService.class, OrgAccess.class})
class VaultAndSuccessionControllerTest {

    private static final UUID ORG = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean VaultService vault;
    @MockitoBean SuccessionService succession;

    private String token(String role, boolean mfa) {
        return "Bearer " + jwt.issue("u@x.ae", List.of(role), List.of(ORG), mfa, Duration.ofMinutes(5));
    }

    private final MockMultipartFile file = new MockMultipartFile("file", "aoa.pdf", "application/pdf", "%PDF-1.7".getBytes());

    @Test
    void secretaryUploadsBoardMemberCannot() throws Exception {
        when(vault.upload(eq(ORG), eq("AoA"), eq(VaultCategory.ARTICLES_OF_ASSOCIATION), any(), any(), any()))
                .thenReturn(new VaultDocumentRepository.Summary(UUID.randomUUID(), "AoA", VaultCategory.ARTICLES_OF_ASSOCIATION,
                        "aoa.pdf", "application/pdf", 8, "abc", "u", Instant.now()));
        mvc.perform(multipart("/api/v1/organizations/" + ORG + "/vault").file(file).param("title", "AoA")
                        .param("category", "ARTICLES_OF_ASSOCIATION").header("Authorization", token("CORPORATE_SECRETARY", true)))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/v1/organizations/" + ORG + "/vault").file(file).param("title", "AoA")
                        .param("category", "ARTICLES_OF_ASSOCIATION").header("Authorization", token("BOARD_MEMBER", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadsCarryHardeningHeaders() throws Exception {
        var d = new com.equigcc.vault.VaultDocument();
        d.setFileName("aoa.pdf");
        d.setContentType("application/pdf");
        d.setSha256("abc");
        d.setContent("%PDF-1.7".getBytes());
        UUID id = UUID.randomUUID();
        when(vault.download(ORG, id)).thenReturn(d);
        mvc.perform(get("/api/v1/organizations/" + ORG + "/vault/" + id + "/content").header("Authorization", token("LEGAL_AUDITOR", true)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"aoa.pdf\""));
        mvc.perform(get("/api/v1/organizations/" + ORG + "/vault").header("Authorization", token("SHAREHOLDER", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void executingARuleNeedsAnMfaVerifiedFounderOrSecretary() throws Exception {
        UUID id = UUID.randomUUID();
        when(succession.execute(eq(ORG), eq(id), any(), any())).thenReturn(List.of());
        mvc.perform(post("/api/v1/organizations/" + ORG + "/succession-rules/" + id + "/execute")
                        .header("Authorization", token("FOUNDER", true)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/organizations/" + ORG + "/succession-rules/" + id + "/execute")
                        .header("Authorization", token("FOUNDER", false)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/organizations/" + ORG + "/succession-rules/" + id + "/execute")
                        .header("Authorization", token("BOARD_MEMBER", true)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ruleValidationRejectsNonPositiveRatios() throws Exception {
        mvc.perform(post("/api/v1/organizations/" + ORG + "/succession-rules").header("Authorization", token("FOUNDER", true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"r\",\"trigger\":\"CUSTOM\",\"fromClassId\":\"" + UUID.randomUUID() + "\",\"toClassId\":\""
                                + UUID.randomUUID() + "\",\"ratioNumerator\":0,\"ratioDenominator\":1,\"legalReference\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
