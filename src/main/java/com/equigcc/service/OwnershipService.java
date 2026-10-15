package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.OwnershipLink;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.ShareholderType;
import com.equigcc.repository.OrganizationRepository;
import com.equigcc.repository.OwnershipLinkRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.web.dto.OwnershipDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Records who owns each corporate shareholder so the register can be looked through to natural persons. */
@Service
public class OwnershipService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OwnershipLinkRepository links;
    private final ShareholderRepository shareholders;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public OwnershipService(OwnershipLinkRepository links, ShareholderRepository shareholders,
                            OrganizationRepository organizations, AuditService audit) {
        this.links = links;
        this.shareholders = shareholders;
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OwnershipLink> all(UUID orgId) {
        return links.findByOrganization(orgId);
    }

    @Transactional(readOnly = true)
    public List<OwnershipLink> ownersOf(UUID orgId, UUID ownedId) {
        shareholders.findByIdAndOrganizationId(ownedId, orgId)
                .orElseThrow(() -> new NotFoundException("Shareholder " + ownedId + " not found"));
        return links.findOwnersOf(orgId, ownedId);
    }

    /** Replaces the full set of recorded owners of a corporate shareholder. */
    @Transactional
    public List<OwnershipLink> replaceOwners(UUID orgId, UUID ownedId, List<OwnershipDtos.OwnerInput> inputs, String user) {
        Shareholder owned = shareholders.findByIdAndOrganizationId(ownedId, orgId)
                .orElseThrow(() -> new NotFoundException("Shareholder " + ownedId + " not found"));
        if (owned.getHolderType() != ShareholderType.CORPORATE) {
            throw new BusinessRuleException("Only a corporate shareholder can have recorded owners");
        }
        Set<UUID> seen = new HashSet<>();
        BigDecimal econ = BigDecimal.ZERO;
        BigDecimal vote = BigDecimal.ZERO;
        List<OwnershipLink> fresh = new ArrayList<>();
        var org = organizations.getReferenceById(orgId);
        for (var in : inputs) {
            if (in.ownerId().equals(ownedId)) {
                throw new BusinessRuleException("A company cannot own itself");
            }
            if (!seen.add(in.ownerId())) {
                throw new BusinessRuleException("Each owner can be listed only once");
            }
            Shareholder owner = shareholders.findByIdAndOrganizationId(in.ownerId(), orgId)
                    .orElseThrow(() -> new NotFoundException("Owner " + in.ownerId() + " not found in organization"));
            var link = new OwnershipLink();
            link.setOrganization(org);
            link.setOwner(owner);
            link.setOwned(owned);
            link.setEconomicPct(in.economicPct());
            link.setVotingPct(in.votingPct() != null ? in.votingPct() : in.economicPct());
            econ = econ.add(link.getEconomicPct());
            vote = vote.add(link.getVotingPct());
            fresh.add(link);
        }
        if (econ.compareTo(HUNDRED) > 0 || vote.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("Recorded owners cannot add up to more than 100%");
        }
        rejectCycles(orgId, ownedId, fresh);

        links.deleteAll(links.findOwnersOf(orgId, ownedId));
        links.flush();
        links.saveAll(fresh);
        audit.record(orgId, "OwnershipLink", ownedId, AuditAction.UPDATE,
                "set owners of " + owned.getFullNameEn() + ": " + fresh.size() + " owner(s), "
                        + econ.stripTrailingZeros().toPlainString() + "% economic", user);
        return fresh;
    }

    private void rejectCycles(UUID orgId, UUID ownedId, List<OwnershipLink> fresh) {
        Map<UUID, List<UUID>> ownersOf = new HashMap<>();
        for (OwnershipLink l : links.findByOrganization(orgId)) {
            if (!l.getOwned().getId().equals(ownedId)) {
                ownersOf.computeIfAbsent(l.getOwned().getId(), k -> new ArrayList<>()).add(l.getOwner().getId());
            }
        }
        for (OwnershipLink l : fresh) {
            ownersOf.computeIfAbsent(ownedId, k -> new ArrayList<>()).add(l.getOwner().getId());
        }
        // A cycle exists if ownedId can be reached by walking owners upward from itself.
        Set<UUID> visited = new HashSet<>();
        var stack = new java.util.ArrayDeque<UUID>(ownersOf.getOrDefault(ownedId, List.of()));
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (cur.equals(ownedId)) {
                throw new BusinessRuleException("These owners would create circular ownership");
            }
            if (visited.add(cur)) {
                stack.addAll(ownersOf.getOrDefault(cur, List.of()));
            }
        }
    }
}
