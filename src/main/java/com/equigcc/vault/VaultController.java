package com.equigcc.vault;

import com.equigcc.common.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/vault")
@Tag(name = "Document vault")
public class VaultController {

    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and @orgAccess.canAccess(authentication, #orgId)";
    private static final String WRITE = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and @orgAccess.canAccess(authentication, #orgId)";

    private final VaultService vault;

    public VaultController(VaultService vault) {
        this.vault = vault;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<VaultDocumentRepository.Summary> list(@PathVariable UUID orgId) {
        return vault.list(orgId);
    }

    @Operation(summary = "Store a document (PDF, PNG, JPEG, DOCX or text, up to 10 MB); the type is checked from the file's bytes")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public VaultDocumentRepository.Summary upload(@PathVariable UUID orgId, @RequestParam String title,
                                                  @RequestParam VaultCategory category, @RequestParam("file") MultipartFile file,
                                                  Authentication auth) throws IOException {
        if (title.isBlank() || title.length() > 200) {
            throw new BusinessRuleException("A title of up to 200 characters is required");
        }
        return vault.upload(orgId, title, category, file.getOriginalFilename(), file.getBytes(), auth.getName());
    }

    @Operation(summary = "Download a stored document; its SHA-256 is re-verified first")
    @GetMapping("/{id}/content")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> download(@PathVariable UUID orgId, @PathVariable UUID id) {
        var d = vault.download(orgId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header("Content-Disposition", "attachment; filename=\"" + d.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header("Cache-Control", "no-store")
                .header("X-Content-SHA256", d.getSha256())
                .body(d.getContent());
    }
}
