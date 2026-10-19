package com.equigcc.compliance;

import com.equigcc.audit.AuditAction;
import com.equigcc.audit.AuditLogRepository;
import com.equigcc.audit.AuditService;
import com.equigcc.common.BusinessRuleException;
import com.equigcc.common.NotFoundException;
import com.equigcc.compliance.ComplianceRules.Kind;
import com.equigcc.compliance.ComplianceRules.Rule;
import com.equigcc.organization.Jurisdiction;
import com.equigcc.organization.Organization;
import com.equigcc.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ComplianceService {

    static final List<String> REGISTER_ENTITIES = List.of("ShareIssuance", "Shareholder", "ShareClass", "OwnershipLink");
    static final int SOON_DAYS = 7;
    public static final String DISCLAIMER = "Deadlines are a reading of the published rules and are not legal advice. Confirm each one with counsel.";

    public enum Status { UP_TO_DATE, PENDING, DUE_SOON, OVERDUE }

    public record Item(String key, String titleEn, String titleAr, String basis, String note, LocalDate dueDate, Status status,
                       LocalDate lastFiledOn, String lastReference) { }

    public record View(Jurisdiction jurisdiction, String disclaimer, List<Item> items) { }

    private final OrganizationRepository organizations;
    private final ComplianceFilingRepository filings;
    private final AuditLogRepository auditLog;
    private final AuditService audit;
    private final Clock clock;

    public ComplianceService(OrganizationRepository organizations, ComplianceFilingRepository filings, AuditLogRepository auditLog,
                             AuditService audit, Clock clock) {
        this.organizations = organizations;
        this.filings = filings;
        this.auditLog = auditLog;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public View view(UUID orgId) {
        Organization org = org(orgId);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        List<ComplianceFiling> all = filings.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        List<Item> items = ComplianceRules.ALL.stream()
                .filter(r -> r.jurisdictions().contains(org.getJurisdiction()))
                .map(r -> evaluate(r, org, all.stream().filter(f -> f.getRuleKey().equals(r.key())).findFirst().orElse(null), today))
                .sorted(Comparator.comparing(Item::status).reversed().thenComparing(i -> i.dueDate() == null ? LocalDate.MAX : i.dueDate()))
                .toList();
        return new View(org.getJurisdiction(), DISCLAIMER, items);
    }

    @Transactional
    public Item recordFiling(UUID orgId, String key, LocalDate filedOn, String reference, String user) {
        Organization org = org(orgId);
        Rule rule = ComplianceRules.ALL.stream().filter(r -> r.key().equals(key) && r.jurisdictions().contains(org.getJurisdiction()))
                .findFirst().orElseThrow(() -> new NotFoundException("No such filing for this company's jurisdiction"));
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate on = filedOn == null ? today : filedOn;
        if (on.isAfter(today)) {
            throw new BusinessRuleException("A filing cannot be dated in the future");
        }
        ComplianceFiling f = filings.save(new ComplianceFiling(orgId, key, on, reference == null || reference.isBlank() ? null : reference.strip(), user));
        audit.record(orgId, "ComplianceFiling", f.getId(), AuditAction.CREATE, "filed " + key + " on " + on, user);
        return evaluate(rule, org, f, today);
    }

    private Item evaluate(Rule r, Organization org, ComplianceFiling last, LocalDate today) {
        LocalDate due = r.kind() == Kind.CHANGE ? changeDue(r, org, last) : annualDue(r, org, last);
        Status status = due == null ? Status.UP_TO_DATE
                : due.isBefore(today) ? Status.OVERDUE
                : ChronoUnit.DAYS.between(today, due) <= SOON_DAYS ? Status.DUE_SOON : Status.PENDING;
        return new Item(r.key(), r.titleEn(), r.titleAr(), r.basis(), r.note(), due, status,
                last == null ? null : last.getFiledOn(), last == null ? null : last.getReference());
    }

    /** Due from the earliest register change not yet covered by a filing. */
    private LocalDate changeDue(Rule r, Organization org, ComplianceFiling last) {
        Instant since = last == null ? Instant.EPOCH
                : last.getCreatedAt() != null ? last.getCreatedAt() : last.getFiledOn().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return auditLog.findFirstByOrganizationIdAndEntityNameInAndOccurredAtAfterOrderByIdAsc(org.getId(), REGISTER_ENTITIES, since)
                .map(e -> {
                    LocalDate day = e.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
                    return r.workingDays() ? addWorkingDays(day, r.days(), ComplianceRules.weekend(org.getJurisdiction())) : day.plusDays(r.days());
                }).orElse(null);
    }

    /** The January filing: the next one after the last filing, or after the company was created. */
    private LocalDate annualDue(Rule r, Organization org, ComplianceFiling last) {
        LocalDate base = last != null ? last.getFiledOn() : org.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        int year = base.getYear() + (last != null && base.getMonthValue() <= r.annualMonth() ? 0 : 1);
        if (last != null && base.getMonthValue() <= r.annualMonth()) {
            return null; // already filed in this year's window
        }
        return LocalDate.of(year, r.annualMonth(), 1).plusMonths(1).minusDays(1);
    }

    static LocalDate addWorkingDays(LocalDate from, int n, Set<DayOfWeek> weekend) {
        LocalDate d = from;
        int left = n;
        while (left > 0) {
            d = d.plusDays(1);
            if (!weekend.contains(d.getDayOfWeek())) {
                left--;
            }
        }
        return d;
    }

    private Organization org(UUID orgId) {
        return organizations.findById(orgId).orElseThrow(() -> new NotFoundException("Organization " + orgId + " not found"));
    }
}
