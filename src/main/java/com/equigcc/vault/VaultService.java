package com.equigcc.vault;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Stores legal documents (articles of association, notary receipts, agreements) with an integrity hash. */
@Service
public class VaultService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final VaultDocumentRepository documents;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public VaultService(VaultDocumentRepository documents, OrganizationRepository organizations, AuditService audit) {
        this.documents = documents;
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<VaultDocumentRepository.Summary> list(UUID orgId) {
        return documents.summaries(orgId);
    }

    @Transactional
    public VaultDocumentRepository.Summary upload(UUID orgId, String title, VaultCategory category, String fileName,
                                                  byte[] bytes, String user) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("The file is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessRuleException("Files can be at most " + (MAX_BYTES / 1024 / 1024) + " MB");
        }
        String type = sniff(bytes);
        if (type == null) {
            throw new BusinessRuleException("Only PDF, PNG, JPEG, Word (.docx) and plain text files are accepted");
        }
        var org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        var d = new VaultDocument();
        d.setOrganization(org);
        d.setTitle(title.strip());
        d.setCategory(category);
        d.setFileName(safeName(fileName, type));
        d.setContentType(type);
        d.setSizeBytes(bytes.length);
        d.setSha256(sha256(bytes));
        d.setContent(bytes);
        d.setUploadedBy(user);
        d = documents.save(d);
        audit.record(orgId, "VaultDocument", d.getId(), AuditAction.CREATE,
                "stored " + category + " \"" + d.getTitle() + "\" (" + d.getFileName() + ", " + d.getSizeBytes() + " bytes, sha256 "
                        + d.getSha256().substring(0, 12) + "...)", user);
        return new VaultDocumentRepository.Summary(d.getId(), d.getTitle(), d.getCategory(), d.getFileName(), d.getContentType(),
                d.getSizeBytes(), d.getSha256(), d.getUploadedBy(), d.getCreatedAt());
    }

    /** Returns the document after re-checking the stored bytes against the recorded hash. */
    @Transactional(readOnly = true)
    public VaultDocument download(UUID orgId, UUID id) {
        VaultDocument d = documents.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
        if (!sha256(d.getContent()).equals(d.getSha256())) {
            throw new IllegalStateException("Stored document " + id + " no longer matches its recorded SHA-256");
        }
        return d;
    }

    /** Decides the content type from the file's own bytes; the client's claim is never trusted. */
    static String sniff(byte[] b) {
        if (startsWith(b, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        if (startsWith(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(b, 'P', 'K', 3, 4) && containsAscii(b, "word/")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (looksLikeText(b)) {
            return "text/plain";
        }
        return null;
    }

    private static boolean startsWith(byte[] b, int... sig) {
        if (b.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((b[i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAscii(byte[] b, String needle) {
        // The docx directory lists its parts in the zip central directory near the end; scan the whole (capped) file.
        return new String(b, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static boolean looksLikeText(byte[] b) {
        int n = Math.min(b.length, 8192);
        for (int i = 0; i < n; i++) {
            int c = b[i] & 0xFF;
            if (c == 0 || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) {
                return false;
            }
        }
        return true;
    }

    static String safeName(String raw, String type) {
        String n = raw == null ? "" : raw;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        n = n.replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").strip();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            n = "document";
        }
        if (n.length() > 150) {
            n = n.substring(n.length() - 150);
        }
        String ext = switch (type) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "text/plain" -> ".txt";
            default -> ".docx";
        };
        // The extension always matches the sniffed type, so a script cannot be stored under a harmless-looking type.
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        return base + ext;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
