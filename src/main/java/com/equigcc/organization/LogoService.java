package com.equigcc.organization;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LogoService {

    static final int MAX_BYTES = 512 * 1024;

    private final OrganizationRepository organizations;
    private final OrganizationLogoRepository logos;
    private final AuditService audit;
    private final Clock clock;

    public LogoService(OrganizationRepository organizations, OrganizationLogoRepository logos, AuditService audit, Clock clock) {
        this.organizations = organizations;
        this.logos = logos;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Instant replace(UUID orgId, byte[] bytes, String user) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new BusinessRuleException("The logo must be an image of up to 512 KB");
        }
        String type = sniff(bytes);
        if (type == null) {
            throw new BusinessRuleException("Only PNG, JPEG or WebP images are accepted as a logo");
        }
        Organization org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        Instant now = clock.instant();
        OrganizationLogo logo = logos.findById(orgId).orElseGet(() -> new OrganizationLogo(orgId));
        logo.replace(type, bytes, sha256(bytes), user, now);
        logos.save(logo);
        org.setLogoUpdatedAt(now);
        audit.record(orgId, "Organization", orgId, AuditAction.UPDATE,
                "logo replaced (" + type + ", " + bytes.length + " bytes)", user);
        return now;
    }

    @Transactional
    public void remove(UUID orgId, String user) {
        Organization org = organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
        if (logos.existsById(orgId)) {
            logos.deleteById(orgId);
            org.setLogoUpdatedAt(null);
            audit.record(orgId, "Organization", orgId, AuditAction.UPDATE, "logo removed", user);
        }
    }

    @Transactional(readOnly = true)
    public OrganizationLogo get(UUID orgId) {
        return logos.findById(orgId).orElseThrow(() -> new NotFoundException("This company has no logo"));
    }

    /** The type comes from the file's own bytes; SVG is refused because it can carry scripts. */
    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
