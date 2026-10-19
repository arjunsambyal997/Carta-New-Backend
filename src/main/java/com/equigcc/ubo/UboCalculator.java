package com.equigcc.ubo;

import com.equigcc.captable.ShareholderType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure look-through calculation. Each register holder's economic share (fully diluted) and voting share is pushed down
 * through corporate holders using the recorded ownership links, multiplying along each path and summing across paths.
 */
public final class UboCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 8;

    private UboCalculator() {
    }

    public record Party(UUID id, ShareholderType type, String nameEn, String nameAr, String nationality, String documentNo) {
    }

    /** {@code owner} holds these percentages of the corporate {@code owned}. */
    public record Link(UUID owner, UUID owned, BigDecimal economicPct, BigDecimal votingPct) {
    }

    /** A holder's direct position on the register. */
    public record Position(UUID holderId, BigDecimal economicPct, BigDecimal votingPct) {
    }

    public record Result(List<UboReport.Person> beneficialOwners, List<UboReport.Person> belowThreshold,
                         List<UboReport.Gap> gaps, List<String> warnings) {
    }

    private static final class Acc {
        BigDecimal econ = BigDecimal.ZERO;
        BigDecimal vote = BigDecimal.ZERO;
        boolean direct;
        final List<UboReport.Path> paths = new ArrayList<>();
    }

    public static Result calculate(Map<UUID, Party> parties, List<Position> positions, List<Link> links,
                                   BigDecimal thresholdPct, String registerName) {
        Map<UUID, List<Link>> ownersOf = new HashMap<>();
        for (Link l : links) {
            ownersOf.computeIfAbsent(l.owned(), k -> new ArrayList<>()).add(l);
        }
        Map<UUID, Acc> persons = new LinkedHashMap<>();
        Map<UUID, UboReport.Gap> gaps = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (Position p : positions) {
            Party party = parties.get(p.holderId());
            if (party == null) {
                continue;
            }
            walk(party, p.economicPct(), p.votingPct(), List.of(registerName, party.nameEn()), new HashSet<>(Set.of(party.id())),
                    true, parties, ownersOf, persons, gaps, warnings);
        }

        List<UboReport.Person> ubos = new ArrayList<>();
        List<UboReport.Person> others = new ArrayList<>();
        for (var e : persons.entrySet()) {
            Party party = parties.get(e.getKey());
            Acc a = e.getValue();
            BigDecimal econ = a.econ.setScale(4, RoundingMode.HALF_UP);
            BigDecimal vote = a.vote.setScale(4, RoundingMode.HALF_UP);
            boolean byEcon = econ.compareTo(thresholdPct) >= 0;
            boolean byVote = vote.compareTo(thresholdPct) >= 0;
            String why = byEcon && byVote ? "BOTH" : byEcon ? "ECONOMIC" : byVote ? "VOTING" : null;
            var person = new UboReport.Person(party.id(), party.nameEn(), party.nameAr(), party.nationality(), party.documentNo(),
                    econ, vote, why, a.direct, a.paths);
            (why != null ? ubos : others).add(person);
        }
        ubos.sort((x, y) -> y.economicPct().compareTo(x.economicPct()));
        others.sort((x, y) -> y.economicPct().compareTo(x.economicPct()));
        return new Result(ubos, others, new ArrayList<>(gaps.values()), warnings);
    }

    private static void walk(Party party, BigDecimal econ, BigDecimal vote, List<String> chain, Set<UUID> visiting, boolean first,
                             Map<UUID, Party> parties, Map<UUID, List<Link>> ownersOf, Map<UUID, Acc> persons,
                             Map<UUID, UboReport.Gap> gaps, List<String> warnings) {
        if (econ.signum() == 0 && vote.signum() == 0) {
            return;
        }
        if (party.type() == ShareholderType.INDIVIDUAL) {
            Acc a = persons.computeIfAbsent(party.id(), k -> new Acc());
            a.econ = a.econ.add(econ);
            a.vote = a.vote.add(vote);
            a.direct |= first;
            a.paths.add(new UboReport.Path(chain, round(econ), round(vote)));
            return;
        }
        List<Link> owners = ownersOf.getOrDefault(party.id(), List.of());
        if (owners.isEmpty()) {
            addGap(gaps, party, econ, vote, "No owners recorded for this company");
            return;
        }
        BigDecimal econLeft = HUNDRED;
        BigDecimal voteLeft = HUNDRED;
        for (Link l : owners) {
            econLeft = econLeft.subtract(l.economicPct());
            voteLeft = voteLeft.subtract(l.votingPct());
            Party owner = parties.get(l.owner());
            if (owner == null) {
                continue;
            }
            if (visiting.contains(owner.id())) {
                warnings.add("Circular ownership: " + owner.nameEn() + " owns " + party.nameEn() + " which is already above it in the chain");
                continue;
            }
            Set<UUID> next = new HashSet<>(visiting);
            next.add(owner.id());
            List<String> nextChain = new ArrayList<>(chain);
            nextChain.add(owner.nameEn());
            walk(owner, econ.multiply(l.economicPct()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP),
                    vote.multiply(l.votingPct()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP),
                    nextChain, next, false, parties, ownersOf, persons, gaps, warnings);
        }
        BigDecimal econGap = econ.multiply(econLeft.max(BigDecimal.ZERO)).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal voteGap = vote.multiply(voteLeft.max(BigDecimal.ZERO)).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        if (econGap.signum() > 0 || voteGap.signum() > 0) {
            addGap(gaps, party, econGap, voteGap, "Recorded owners add up to less than 100%");
        }
    }

    private static void addGap(Map<UUID, UboReport.Gap> gaps, Party party, BigDecimal econ, BigDecimal vote, String reason) {
        gaps.merge(party.id(),
                new UboReport.Gap(party.id(), party.nameEn(), party.nameAr(), round(econ), round(vote), reason),
                (a, b) -> new UboReport.Gap(a.shareholderId(), a.nameEn(), a.nameAr(),
                        a.economicPct().add(b.economicPct()), a.votingPct().add(b.votingPct()), a.reason()));
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP);
    }
}
