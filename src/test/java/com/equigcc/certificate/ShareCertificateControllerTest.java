package com.equigcc.certificate;

import com.equigcc.auth.JwtService;
import com.equigcc.auth.OrgAccess;
import com.equigcc.captable.CapTableService;
import com.equigcc.captable.Currency;
import com.equigcc.captable.InstrumentType;
import com.equigcc.captable.ShareClass;
import com.equigcc.captable.Shareholder;
import com.equigcc.captable.WaterfallService;
import com.equigcc.config.SecurityConfig;
import com.equigcc.organization.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShareCertificateController.class)
@Import({SecurityConfig.class, JwtService.class, OrgAccess.class})
class ShareCertificateControllerTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID HOLDER = UUID.randomUUID();
    private static final UUID CLASS = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CertificateIssuanceService issuanceService;
    @MockitoBean CapTableService capTableService;
    @MockitoBean WaterfallService waterfallService;
    @MockitoBean com.equigcc.notice.NoticeService notices;
    @MockitoBean com.equigcc.webhook.WebhookService webhooks;
    @MockitoBean com.equigcc.certificate.CertificatePdfService pdfService;
    @MockitoBean com.equigcc.approval.ApprovalService approvals;

    private final String body = """
            {"shareholderId":"%s","shareClassId":"%s","quantity":1000,"pricePerShare":10.00}
            """.formatted(HOLDER, CLASS);

    @Test
    void founderWithMfaIssuesCertificate() throws Exception {
        when(issuanceService.issue(any())).thenReturn(sampleIssuance());

        mvc.perform(post("/api/v1/organizations/{orgId}/certificates", ORG)
                        .header("Authorization", "Bearer " + token("FOUNDER", ORG, true))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/certificates/ACME-000001")))
                .andExpect(jsonPath("$.certificateNo").value("ACME-000001"))
                .andExpect(jsonPath("$.totalConsideration").value(10000.0))
                .andExpect(jsonPath("$.verificationUrl").value(org.hamcrest.Matchers.containsString("ACME-000001?h=abc")));
    }

    @Test
    void rejectsSessionWithoutMfa() throws Exception {
        mvc.perform(post("/api/v1/organizations/{orgId}/certificates", ORG)
                        .header("Authorization", "Bearer " + token("FOUNDER", ORG, false))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(issuanceService, never()).issue(any());
    }

    @Test
    void rejectsCallerFromAnotherOrganization() throws Exception {
        mvc.perform(post("/api/v1/organizations/{orgId}/certificates", ORG)
                        .header("Authorization", "Bearer " + token("FOUNDER", UUID.randomUUID(), true))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void shareholderRoleCannotIssue() throws Exception {
        mvc.perform(post("/api/v1/organizations/{orgId}/certificates", ORG)
                        .header("Authorization", "Bearer " + token("SHAREHOLDER", ORG, true))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessRuleViolationReturns422() throws Exception {
        when(issuanceService.issue(any())).thenThrow(new IssuanceRejectedException("below par value"));

        mvc.perform(post("/api/v1/organizations/{orgId}/certificates", ORG)
                        .header("Authorization", "Bearer " + token("FOUNDER", ORG, true))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("below par value"));
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mvc.perform(post("/api/v1/organizations/{orgId}/certificates", ORG)
                        .header("Authorization", "Bearer " + token("FOUNDER", ORG, true))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareholderId\":\"%s\",\"shareClassId\":\"%s\",\"quantity\":0,\"pricePerShare\":1}"
                                .formatted(HOLDER, CLASS)))
                .andExpect(status().isBadRequest());
    }

    private String token(String role, UUID org, boolean mfa) {
        return jwt.issue("user-1", List.of(role), List.of(org), mfa, Duration.ofMinutes(5));
    }

    @Test
    void shareholderDownloadsOwnPdfButNotSomeoneElses() throws Exception {
        when(issuanceService.findInOrganization(ORG, "ACME-000001")).thenReturn(sampleIssuance());
        when(pdfService.render(any())).thenReturn("%PDF-1.7".getBytes());
        String own = jwt.issue("h@x.ae", List.of("SHAREHOLDER"), List.of(ORG), true, HOLDER, Duration.ofMinutes(5));
        String other = jwt.issue("o@x.ae", List.of("SHAREHOLDER"), List.of(ORG), true, UUID.randomUUID(), Duration.ofMinutes(5));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/organizations/" + ORG + "/certificates/ACME-000001/pdf")
                        .header("Authorization", "Bearer " + own))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/organizations/" + ORG + "/certificates/ACME-000001/pdf")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareholderCannotListAllCertificates() throws Exception {
        String own = jwt.issue("h@x.ae", List.of("SHAREHOLDER"), List.of(ORG), true, HOLDER, Duration.ofMinutes(5));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/organizations/" + ORG + "/certificates")
                        .header("Authorization", "Bearer " + own))
                .andExpect(status().isForbidden());
    }

    private static ShareIssuance sampleIssuance() {
        var org = new Organization();
        org.setId(ORG);
        var holder = new Shareholder();
        holder.setId(HOLDER);
        holder.setFullNameEn("Fatima Al Mansoori");
        holder.setFullNameAr("فاطمة المنصوري");
        var cls = new ShareClass();
        cls.setId(CLASS);
        cls.setName("Class A");
        var i = new ShareIssuance();
        i.setId(UUID.randomUUID());
        i.setOrganization(org);
        i.setShareholder(holder);
        i.setShareClass(cls);
        i.setCertificateNo("ACME-000001");
        i.setInstrumentType(InstrumentType.SHARES);
        i.setQuantity(1000);
        i.setPricePerShare(new BigDecimal("10.00"));
        i.setCurrency(Currency.AED);
        i.setDateIssued(LocalDate.of(2026, 10, 15));
        i.setRecordHash("abc");
        return i;
    }
}
