package com.equigcc.organization;

import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final OrganizationLogoRepository logos = mock(OrganizationLogoRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final LogoService service = new LogoService(orgs, logos, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID id = UUID.randomUUID();
    private final Organization org = new Organization();

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    @Test
    void storesAPngAndMarksTheOrganization() {
        when(orgs.findById(id)).thenReturn(Optional.of(org));
        when(logos.findById(id)).thenReturn(Optional.empty());
        assertThat(service.replace(id, png(), "founder@x.ae")).isEqualTo(NOW);
        assertThat(org.getLogoUpdatedAt()).isEqualTo(NOW);
        verify(logos).save(any(OrganizationLogo.class));
        verify(audit).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void refusesSvgAndOtherNonImages() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        assertThatThrownBy(() -> service.replace(id, svg, "u")).isInstanceOf(BusinessRuleException.class);
        verify(logos, never()).save(any());
    }

    @Test
    void refusesEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> service.replace(id, new byte[0], "u")).isInstanceOf(BusinessRuleException.class);
        byte[] big = new byte[LogoService.MAX_BYTES + 1];
        System.arraycopy(png(), 0, big, 0, 8);
        assertThatThrownBy(() -> service.replace(id, big, "u")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void recognisesJpegAndWebpFromTheirBytes() {
        assertThat(LogoService.sniff(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).isEqualTo("image/jpeg");
        byte[] webp = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};
        assertThat(LogoService.sniff(webp)).isEqualTo("image/webp");
    }

    @Test
    void removingClearsTheMarker() {
        org.setLogoUpdatedAt(NOW);
        when(orgs.findById(id)).thenReturn(Optional.of(org));
        when(logos.existsById(id)).thenReturn(true);
        service.remove(id, "u");
        assertThat(org.getLogoUpdatedAt()).isNull();
        verify(logos).deleteById(id);
    }

    @Test
    void aMissingLogoIsNotFound() {
        when(logos.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }
}
