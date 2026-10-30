package com.equigcc.auth;

import com.equigcc.common.UnauthorizedException;
import com.equigcc.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, OrgAccess.class})
class AuthControllerTest {

    private static final UUID ORG = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean AuthService auth;
    @MockitoBean com.equigcc.auth.PasswordResetService passwordReset;

    @Test
    void loginIsPublicAndReturnsTheServiceResult() throws Exception {
        when(auth.login(any(), any(), any())).thenReturn(new AuthResult("MFA_REQUIRED", null, "pending", false, ORG, "FOUNDER", "Fatima", null));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fatima@alnoor.ae\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
    }

    @Test
    void badCredentialsReturn401() throws Exception {
        when(auth.login(any(), any(), any())).thenThrow(new UnauthorizedException("Invalid credentials"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fatima@alnoor.ae\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupValidatesPasswordLength() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.ae\",\"password\":\"short\",\"displayName\":\"A\",\"organization\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mfaPendingTokenCannotBeUsedAsAnAccessToken() throws Exception {
        String pending = jwt.issuePending("fatima@alnoor.ae", ORG, Duration.ofMinutes(5));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + pending))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheScopedIdentity() throws Exception {
        String token = jwt.issue("fatima@alnoor.ae", List.of("FOUNDER"), List.of(ORG), true, Duration.ofMinutes(5));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FOUNDER"))
                .andExpect(jsonPath("$.mfaVerified").value(true));
    }

    @Test
    void anonymousCannotReachEnrollment() throws Exception {
        mvc.perform(post("/api/v1/auth/mfa/enroll")).andExpect(status().isForbidden());
    }

    @Test
    void forgotAndResetNeedNoSignInAndForgotAlwaysAccepts() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/password/forgot")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@x.ae\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/password/reset")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"a long enough password\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/password/reset")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"short\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void changePasswordRequiresASession() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/password/change")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"a long enough password\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is4xxClientError());
    }
}
