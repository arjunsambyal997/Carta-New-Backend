package com.equigcc.organization;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/logo")
@Tag(name = "Organization")
public class LogoController {

    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR','SHAREHOLDER') "
            + "and @orgAccess.canAccess(authentication, #orgId)";
    private static final String WRITE = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY') and @orgAccess.canAccess(authentication, #orgId)";

    private final LogoService logos;

    public LogoController(LogoService logos) {
        this.logos = logos;
    }

    @Operation(summary = "The company logo; every member of the company may see it")
    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> get(@PathVariable UUID orgId) {
        OrganizationLogo logo = logos.get(orgId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .eTag("\"" + logo.getSha256() + "\"")
                .body(logo.getContent());
    }

    @Operation(summary = "Set or replace the logo (PNG, JPEG or WebP up to 512 KB; type checked from the bytes)")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    public Map<String, Object> put(@PathVariable UUID orgId, @RequestParam("file") MultipartFile file, Authentication auth)
            throws IOException {
        return Map.of("logoUpdatedAt", logos.replace(orgId, file.getBytes(), auth.getName()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void delete(@PathVariable UUID orgId, Authentication auth) {
        logos.remove(orgId, auth.getName());
    }
}
