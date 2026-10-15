package com.equigcc.service;

import com.equigcc.domain.Organization;
import com.equigcc.domain.OwnershipLink;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.ShareholderType;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.OwnershipLinkRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.web.dto.OwnershipDtos.OwnerInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

class OwnershipServiceTest {

    private final UUID org = UUID.randomUUID();
    private final OwnershipLinkRepository links = mock(OwnershipLinkRepository.class);
    private final ShareholderRepository holders = mock(ShareholderRepository.class);
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final OwnershipService service = new OwnershipService(links, holders, orgs, audit);

    private Shareholder holder(ShareholderType type) {
        var s = new Shareholder();
        s.setId(UUID.randomUUID());
        s.setHolderType(type);
        s.setFullNameEn(type + "-" + s.getId().toString().substring(0, 4));
        when(holders.findByIdAndOrganizationId(s.getId(), org)).thenReturn(Optional.of(s));
        return s;
    }

    private static OwnerInput in(Shareholder s, String econ, String vote) {
        return new OwnerInput(s.getId(), new BigDecimal(econ), vote == null ? null : new BigDecimal(vote));
    }

    private static OwnershipLink link(Shareholder owner, Shareholder owned) {
        var l = new OwnershipLink();
        l.setOwner(owner);
        l.setOwned(owned);
        l.setEconomicPct(BigDecimal.TEN);
        l.setVotingPct(BigDecimal.TEN);
        return l;
    }

    @Test
    void savesOwnersAndAudits() {
        var co = holder(ShareholderType.CORPORATE);
        var p = holder(ShareholderType.INDIVIDUAL);
        when(orgs.getReferenceById(org)).thenReturn(new Organization());
        when(links.findOwnersOf(org, co.getId())).thenReturn(List.of());
        when(links.findByOrganization(org)).thenReturn(List.of());
        var saved = service.replaceOwners(org, co.getId(), List.of(in(p, "60", null)), "u");
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getVotingPct()).isEqualByComparingTo("60");
        verify(audit).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void individualsCannotHaveOwners() {
        var p = holder(ShareholderType.INDIVIDUAL);
        assertThatThrownBy(() -> service.replaceOwners(org, p.getId(), List.of(), "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("corporate");
    }

    @Test
    void rejectsSelfOwnership() {
        var co = holder(ShareholderType.CORPORATE);
        assertThatThrownBy(() -> service.replaceOwners(org, co.getId(), List.of(in(co, "10", null)), "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("itself");
    }

    @Test
    void rejectsDuplicateOwnersAndOverHundredPercent() {
        var co = holder(ShareholderType.CORPORATE);
        var p = holder(ShareholderType.INDIVIDUAL);
        var q = holder(ShareholderType.INDIVIDUAL);
        when(orgs.getReferenceById(org)).thenReturn(new Organization());
        assertThatThrownBy(() -> service.replaceOwners(org, co.getId(), List.of(in(p, "10", null), in(p, "10", null)), "u"))
                .hasMessageContaining("only once");
        assertThatThrownBy(() -> service.replaceOwners(org, co.getId(), List.of(in(p, "60", null), in(q, "50", null)), "u"))
                .hasMessageContaining("100%");
        assertThatThrownBy(() -> service.replaceOwners(org, co.getId(), List.of(in(p, "50", "50"), in(q, "10", "60")), "u"))
                .hasMessageContaining("100%");
    }

    @Test
    void rejectsCircularOwnership() {
        var a = holder(ShareholderType.CORPORATE);
        var b = holder(ShareholderType.CORPORATE);
        when(orgs.getReferenceById(org)).thenReturn(new Organization());
        // B is already owned by A; making A owned by B closes a loop.
        when(links.findByOrganization(org)).thenReturn(new ArrayList<>(List.of(link(a, b))));
        assertThatThrownBy(() -> service.replaceOwners(org, a.getId(), List.of(in(b, "50", null)), "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("circular");
    }

    @Test
    void unknownOwnerIsNotFound() {
        var co = holder(ShareholderType.CORPORATE);
        when(orgs.getReferenceById(org)).thenReturn(new Organization());
        var stranger = new OwnerInput(UUID.randomUUID(), BigDecimal.TEN, null);
        when(holders.findByIdAndOrganizationId(stranger.ownerId(), org)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.replaceOwners(org, co.getId(), List.of(stranger), "u"))
                .isInstanceOf(NotFoundException.class);
    }
}
