package com.equigcc.apikey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/api-keys")
@Tag(name = "Integrations")
public class ApiKeyController {

    private static final String FOUNDER_ONLY = "hasRole('FOUNDER') and @orgAccess.canAccess(authentication, #orgId)";

    public record CreateRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record View(UUID id, String name, String keyPrefix, String createdBy, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
        static View from(ApiKey k) {
            return new View(k.getId(), k.getName(), k.getKeyPrefix(), k.getCreatedBy(), k.getCreatedAt(), k.getLastUsedAt(), k.getRevokedAt());
        }
    }

    /** {@code key} is the only time the full secret is returned. */
    public record CreatedView(View key, String secret) {
    }

    private final ApiKeyService service;

    public ApiKeyController(ApiKeyService service) {
        this.service = service;
    }

    @Operation(summary = "Create a read-only API key",
            description = "Send it as the X-API-Key header on GET requests. The key is shown once and cannot be retrieved later.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(FOUNDER_ONLY)
    public CreatedView create(@PathVariable UUID orgId, @Valid @RequestBody CreateRequest request, Authentication auth) {
        var created = service.create(orgId, request.name(), auth.getName());
        return new CreatedView(View.from(created.key()), created.plaintext());
    }

    @GetMapping
    @PreAuthorize(FOUNDER_ONLY)
    public List<View> list(@PathVariable UUID orgId) {
        return service.list(orgId).stream().map(View::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(FOUNDER_ONLY)
    public void revoke(@PathVariable UUID orgId, @PathVariable UUID id, Authentication auth) {
        service.revoke(orgId, id, auth.getName());
    }
}
