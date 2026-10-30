package com.equigcc.analytics;

import com.equigcc.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/analytics")
@Tag(name = "Analytics")
public class AnalyticsController {

    private static final String READ = "hasAnyRole('FOUNDER','CORPORATE_SECRETARY','BOARD_MEMBER','LEGAL_AUDITOR') and @orgAccess.canAccess(authentication, #orgId)";

    private final AnalyticsEmbedService embeds;

    public AnalyticsController(AnalyticsEmbedService embeds) {
        this.embeds = embeds;
    }

    @Operation(summary = "A short-lived signed link to this company's analytics dashboard; 404 when analytics is not set up")
    @GetMapping("/embed")
    @PreAuthorize(READ)
    public ResponseEntity<AnalyticsEmbedService.Embed> embed(@PathVariable UUID orgId) {
        var embed = embeds.embed(orgId).orElseThrow(() -> new NotFoundException("Analytics is not set up"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(embed);
    }
}
