package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.DocumentSignature;
import com.equigcc.domain.DocumentType;
import com.equigcc.repository.AppUserRepository;
import com.equigcc.repository.BoardResolutionRepository;
import com.equigcc.repository.DocumentSignatureRepository;
import com.equigcc.repository.ShareIssuanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Electronic signatures on resolutions and certificates. A signature records who signed (a signed-in account that just
 * passed a fresh second-factor check), what they signed (a SHA-256 of the document's fixed content) and when. If the
 * document no longer matches the fingerprint, the signature shows as no longer valid.
 * This is an attestation inside the register, not a qualified or trust-service signature under UAE or KSA e-signature law;
 * where the law requires one, sign the PDF with a licensed provider as well.
 */
@Service
public class SignatureService {

    public static final String DEFAULT_STATEMENT = "I have read this document and confirm it.";

    public record Signer(String email, String role, UUID shareholderId) {
    }

    public record View(UUID id, DocumentType documentType, String documentRef, String signerName, String signerRole, String statement,
                       Instant signedAt, String signatureHash, boolean valid, boolean documentChanged) {
    }

    private record Target(UUID id, String ref, String hash, UUID ownerShareholderId) {
    }

    private final DocumentSignatureRepository signatures;
    private final BoardResolutionRepository resolutions;
    private final ShareIssuanceRepository issuances;
    private final AppUserRepository users;
    private final AuthService auth;
    private final AuditService audit;
    private final Clock clock;

    public SignatureService(DocumentSignatureRepository signatures, BoardResolutionRepository resolutions, ShareIssuanceRepository issuances,
                            AppUserRepository users, AuthService auth, AuditService audit, Clock clock) {
        this.signatures = signatures;
        this.resolutions = resolutions;
        this.issuances = issuances;
        this.users = users;
        this.auth = auth;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public View sign(UUID orgId, DocumentType type, String ref, String code, String statement, Signer signer) {
        Target t = target(orgId, type, ref);
        if ("SHAREHOLDER".equals(signer.role()) && type == DocumentType.CERTIFICATE
                && (signer.shareholderId() == null || !signer.shareholderId().equals(t.ownerShareholderId()))) {
            throw new NotFoundException("Certificate " + ref + " not found");
        }
        if (signatures.existsByOrganizationIdAndDocumentTypeAndDocumentIdAndSignerEmail(orgId, type, t.id(), signer.email())) {
            throw new BusinessRuleException("You have already signed this document");
        }
        auth.confirmSecondFactor(signer.email(), code);
        var user = users.findByEmail(signer.email()).orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        String said = statement == null || statement.isBlank() ? DEFAULT_STATEMENT : statement.trim();
        var s = new DocumentSignature();
        s.setOrganizationId(orgId);
        s.setDocumentType(type);
        s.setDocumentId(t.id());
        s.setDocumentRef(t.ref());
        s.setDocumentHash(t.hash());
        s.setSignerEmail(signer.email());
        s.setSignerName(user.getDisplayName());
        s.setSignerRole(signer.role());
        s.setShareholderId(signer.shareholderId());
        s.setStatement(said);
        s.setSignedAt(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); // the database keeps microseconds
        s.setSignatureHash(signatureHash(s));
        s = signatures.save(s);
        audit.record(orgId, "DocumentSignature", s.getId(), AuditAction.CREATE,
                user.getDisplayName() + " (" + signer.role() + ") signed " + type + " " + t.ref(), signer.email());
        return view(s, t.hash());
    }

    @Transactional(readOnly = true)
    public List<View> list(UUID orgId, DocumentType type, String ref, Signer viewer) {
        Target t = target(orgId, type, ref);
        if ("SHAREHOLDER".equals(viewer.role()) && type == DocumentType.CERTIFICATE
                && (viewer.shareholderId() == null || !viewer.shareholderId().equals(t.ownerShareholderId()))) {
            throw new NotFoundException("Certificate " + ref + " not found");
        }
        return signatures.findByOrganizationIdAndDocumentTypeAndDocumentIdOrderBySignedAtAsc(orgId, type, t.id()).stream()
                .map(s -> view(s, t.hash())).toList();
    }

    private View view(DocumentSignature s, String currentHash) {
        boolean changed = !s.getDocumentHash().equals(currentHash);
        boolean intact = s.getSignatureHash().equals(signatureHash(s));
        return new View(s.getId(), s.getDocumentType(), s.getDocumentRef(), s.getSignerName(), s.getSignerRole(), s.getStatement(), s.getSignedAt(),
                s.getSignatureHash(), intact && !changed, changed);
    }

    private Target target(UUID orgId, DocumentType type, String ref) {
        switch (type) {
            case RESOLUTION -> {
                UUID id;
                try {
                    id = UUID.fromString(ref);
                } catch (IllegalArgumentException e) {
                    throw new NotFoundException("Resolution " + ref + " not found");
                }
                var r = resolutions.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> new NotFoundException("Resolution " + ref + " not found"));
                String canonical = String.join("|", "RESOLUTION", r.getId().toString(), nz(r.getTitle()), nz(r.getTitleAr()), nz(r.getDescription()),
                        r.getThreshold().name(), r.getQuorumPct().stripTrailingZeros().toPlainString(), r.getVotingDeadline().toString());
                return new Target(r.getId(), r.getId().toString(), sha256(canonical), null);
            }
            case CERTIFICATE -> {
                var c = issuances.findByCertificateNo(ref).filter(i -> i.getOrganization().getId().equals(orgId))
                        .orElseThrow(() -> new NotFoundException("Certificate " + ref + " not found"));
                return new Target(c.getId(), c.getCertificateNo(), c.getRecordHash(), c.getShareholder().getId());
            }
            default -> throw new BusinessRuleException("Unknown document type");
        }
    }

    static String signatureHash(DocumentSignature s) {
        return sha256(String.join("|", "sig", s.getOrganizationId().toString(), s.getDocumentType().name(), s.getDocumentId().toString(),
                s.getDocumentHash(), s.getSignerEmail(), s.getSignerRole(), s.getSignedAt().toString(), s.getStatement()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
