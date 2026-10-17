package com.equigcc.vault;

import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.organization.Organization;
import com.equigcc.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultServiceTest {

    private final UUID org = UUID.randomUUID();
    private final VaultDocumentRepository docs = mock(VaultDocumentRepository.class);
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final VaultService service = new VaultService(docs, orgs, audit);

    private static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void sniffsTypesFromBytesNotFromNames() {
        assertThat(VaultService.sniff(pdf())).isEqualTo("application/pdf");
        assertThat(VaultService.sniff(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0})).isEqualTo("image/png");
        assertThat(VaultService.sniff(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).isEqualTo("image/jpeg");
        assertThat(VaultService.sniff("Articles of association\nArticle 1".getBytes(StandardCharsets.UTF_8))).isEqualTo("text/plain");
        assertThat(VaultService.sniff("نص عربي".getBytes(StandardCharsets.UTF_8))).isEqualTo("text/plain");
        var docx = new byte[]{'P', 'K', 3, 4, 0, 0};
        assertThat(VaultService.sniff(docx)).isNull();
        assertThat(VaultService.sniff("PK\u0003\u0004....word/document.xml".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void rejectsExecutablesScriptsAndBinaryBlobs() {
        assertThat(VaultService.sniff(new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0})).isNull();      // Windows executable
        assertThat(VaultService.sniff(new byte[]{(byte) 0x7F, 'E', 'L', 'F', 2, 1, 1, 0})).isNull(); // ELF
        assertThat(VaultService.sniff(new byte[]{0, 1, 2, 3})).isNull();
    }

    @Test
    void fileNamesAreSanitisedAndForcedToTheRealExtension() {
        assertThat(VaultService.safeName("../../etc/passwd.exe", "application/pdf")).isEqualTo("passwd.pdf");
        assertThat(VaultService.safeName("C:\\temp\\a\"b<c>.html", "text/plain")).isEqualTo("a_b_c_.txt");
        assertThat(VaultService.safeName("", "image/png")).isEqualTo("document.png");
        assertThat(VaultService.safeName("..", "image/png")).isEqualTo("document.png");
        assertThat(VaultService.safeName("x".repeat(400) + ".pdf", "application/pdf")).hasSizeLessThanOrEqualTo(154);
    }

    @Test
    void uploadStoresHashAndAudits() {
        when(orgs.findById(org)).thenReturn(Optional.of(new Organization()));
        when(docs.save(any())).thenAnswer(i -> {
            VaultDocument d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        var s = service.upload(org, " Articles ", VaultCategory.ARTICLES_OF_ASSOCIATION, "aoa.pdf", pdf(), "u@x.ae");
        assertThat(s.title()).isEqualTo("Articles");
        assertThat(s.contentType()).isEqualTo("application/pdf");
        assertThat(s.sha256()).isEqualTo(VaultService.sha256(pdf())).hasSize(64);
        verify(audit).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsEmptyOversizedAndUnknownFiles() {
        assertThatThrownBy(() -> service.upload(org, "t", VaultCategory.OTHER, "a.pdf", new byte[0], "u"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.upload(org, "t", VaultCategory.OTHER, "a.pdf", new byte[(int) VaultService.MAX_BYTES + 1], "u"))
                .hasMessageContaining("10 MB");
        assertThatThrownBy(() -> service.upload(org, "t", VaultCategory.OTHER, "a.exe", new byte[]{'M', 'Z', 0, 0}, "u"))
                .hasMessageContaining("Only PDF");
    }

    @Test
    void downloadDetectsTamperedBytes() {
        var d = new VaultDocument();
        d.setContent(pdf());
        d.setSha256(VaultService.sha256(pdf()));
        UUID id = UUID.randomUUID();
        when(docs.findByIdAndOrganizationId(id, org)).thenReturn(Optional.of(d));
        assertThat(service.download(org, id)).isSameAs(d);
        d.setContent("%PDF-1.7 tampered".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> service.download(org, id)).isInstanceOf(IllegalStateException.class).hasMessageContaining("SHA-256");
    }
}
