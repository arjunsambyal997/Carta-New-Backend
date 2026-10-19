package com.equigcc.signature;

import com.equigcc.audit.AuditService;
import com.equigcc.auth.AppUser;
import com.equigcc.auth.AppUserRepository;
import com.equigcc.auth.AuthService;
import com.equigcc.captable.Shareholder;
import com.equigcc.certificate.ShareIssuance;
import com.equigcc.certificate.ShareIssuanceRepository;
import com.equigcc.common.NotFoundException;
import com.equigcc.governance.BoardResolutionRepository;
import com.equigcc.organization.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignatureServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final UUID orgId = UUID.randomUUID();
    private final List<DocumentSignature> store = new ArrayList<>();
    private final DocumentSignatureRepository repo = mock(DocumentSignatureRepository.class);
    private final ShareIssuanceRepository issuances = mock(ShareIssuanceRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private SignatureService service;
    private ShareIssuance cert;
    private Shareholder holder;

    @BeforeEach
    void setUp() {
        var org = new Organization();
        org.setId(orgId);
        holder = new Shareholder();
        holder.setId(UUID.randomUUID());
        cert = new ShareIssuance();
        cert.setId(UUID.randomUUID());
        cert.setOrganization(org);
        cert.setShareholder(holder);
        cert.setCertificateNo("A-000001");
        cert.setRecordHash("a".repeat(64));
        when(issuances.findByCertificateNo("A-000001")).thenReturn(Optional.of(cert));
        var user = new AppUser();
        user.setEmail("f@x.ae");
        user.setDisplayName("Fatima");
        when(users.findByEmail("f@x.ae")).thenReturn(Optional.of(user));
        when(repo.save(any())).thenAnswer(i -> {
            DocumentSignature s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            store.add(s);
            return s;
        });
        when(repo.findByOrganizationIdAndDocumentTypeAndDocumentIdOrderBySignedAtAsc(any(), any(), any())).thenAnswer(i -> store);
        service = new SignatureService(repo, mock(BoardResolutionRepository.class), issuances, users, auth, mock(AuditService.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void signingChecksTheSecondFactorAndRecordsAValidSignature() {
        var v = service.sign(orgId, DocumentType.CERTIFICATE, "A-000001", "123456", null, new SignatureService.Signer("f@x.ae", "FOUNDER", null));
        verify(auth).confirmSecondFactor("f@x.ae", "123456");
        assertThat(v.valid()).isTrue();
        assertThat(v.statement()).isEqualTo(SignatureService.DEFAULT_STATEMENT);
        assertThat(v.signedAt()).isEqualTo(NOW);
    }

    @Test
    void aChangedDocumentOrATamperedSignatureShowsAsInvalid() {
        service.sign(orgId, DocumentType.CERTIFICATE, "A-000001", "123456", null, new SignatureService.Signer("f@x.ae", "FOUNDER", null));
        var viewer = new SignatureService.Signer("f@x.ae", "FOUNDER", null);
        assertThat(service.list(orgId, DocumentType.CERTIFICATE, "A-000001", viewer).getFirst().valid()).isTrue();

        cert.setRecordHash("b".repeat(64));
        var changed = service.list(orgId, DocumentType.CERTIFICATE, "A-000001", viewer).getFirst();
        assertThat(changed.valid()).isFalse();
        assertThat(changed.documentChanged()).isTrue();

        cert.setRecordHash("a".repeat(64));
        store.getFirst().setStatement("something else");
        assertThat(service.list(orgId, DocumentType.CERTIFICATE, "A-000001", viewer).getFirst().valid()).isFalse();
    }

    @Test
    void aShareholderCanOnlySignTheirOwnCertificate() {
        var stranger = new SignatureService.Signer("f@x.ae", "SHAREHOLDER", UUID.randomUUID());
        assertThatThrownBy(() -> service.sign(orgId, DocumentType.CERTIFICATE, "A-000001", "123456", null, stranger)).isInstanceOf(NotFoundException.class);
        var owner = new SignatureService.Signer("f@x.ae", "SHAREHOLDER", holder.getId());
        assertThat(service.sign(orgId, DocumentType.CERTIFICATE, "A-000001", "123456", "mine", owner).valid()).isTrue();
    }
}
