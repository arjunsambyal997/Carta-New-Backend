package com.equigcc.service;

import com.equigcc.domain.AuditAction;
import com.equigcc.domain.ShareClass;
import com.equigcc.domain.Shareholder;
import com.equigcc.domain.ShareholderType;
import com.equigcc.repository.ShareClassRepository;
import com.equigcc.repository.ShareholderRepository;
import com.equigcc.web.dto.SetupDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk load of shareholders and opening issuances from CSV. All or nothing: any bad row rolls the whole file back.
 * A dry run goes through exactly the same code and then rolls back, so what it reports is what an import would do.
 */
@Service
public class ImportService {

    public record RowMessage(int row, String message) {
    }

    public record Result(boolean dryRun, int rows, int created, int skipped, List<RowMessage> errors, List<RowMessage> warnings) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static final String HOLDERS_TEMPLATE = """
            type,name_en,name_ar,nationality,id_number,registration_no,email
            INDIVIDUAL,Fatima Al Noor,فاطمة النور,AE,784-1990-1234567-1,,fatima@example.com
            CORPORATE,Noor Holding LLC,نور القابضة ذ.م.م,AE,,RC-100234,
            """;
    public static final String ISSUANCES_TEMPLATE = """
            holder,share_class,quantity,price_per_share,date
            Fatima Al Noor,Ordinary,100000,1.00,2026-01-15
            Noor Holding LLC,Ordinary,400000,1.00,2026-01-15
            """;

    private final SetupService setup;
    private final CertificateIssuanceService issuance;
    private final ShareholderRepository shareholders;
    private final ShareClassRepository classes;
    private final AuditService audit;

    public ImportService(SetupService setup, CertificateIssuanceService issuance, ShareholderRepository shareholders,
                         ShareClassRepository classes, AuditService audit) {
        this.setup = setup;
        this.issuance = issuance;
        this.shareholders = shareholders;
        this.classes = classes;
        this.audit = audit;
    }

    @Transactional
    public Result importShareholders(UUID orgId, byte[] csv, boolean dryRun, String user) {
        var rows = Csv.parse(csv);
        var errors = new ArrayList<RowMessage>();
        var warnings = new ArrayList<RowMessage>();
        Set<String> known = new HashSet<>();
        shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId).forEach(h -> known.add(key(h.getFullNameEn())));
        int created = 0;
        int skipped = 0;
        for (var r : rows) {
            int n = Integer.parseInt(r.get("_row"));
            String name = r.getOrDefault("name_en", "");
            ShareholderType type = parseEnum(ShareholderType.class, r.getOrDefault("type", ""));
            if (name.isBlank()) {
                errors.add(new RowMessage(n, "name_en is required"));
                continue;
            }
            if (type == null) {
                errors.add(new RowMessage(n, "type must be INDIVIDUAL or CORPORATE"));
                continue;
            }
            String nat = r.getOrDefault("nationality", "");
            if (!nat.isEmpty() && nat.length() != 2) {
                errors.add(new RowMessage(n, "nationality must be a 2-letter country code such as AE"));
                continue;
            }
            if (!known.add(key(name))) {
                warnings.add(new RowMessage(n, name + " is already on the register or earlier in the file; skipped"));
                skipped++;
                continue;
            }
            String id = r.getOrDefault("id_number", "");
            setup.createShareholder(orgId, new SetupDtos.ShareholderRequest(type, name, blank(r.get("name_ar")), null,
                    type == ShareholderType.INDIVIDUAL ? blank(id) : null, blank(nat), null,
                    type == ShareholderType.CORPORATE ? blank(r.get("registration_no")) : null, null, blank(r.get("email"))), user);
            created++;
        }
        return finish(orgId, "shareholders", rows.size(), created, skipped, errors, warnings, dryRun, user);
    }

    @Transactional
    public Result importIssuances(UUID orgId, byte[] csv, boolean dryRun, String user) {
        var rows = Csv.parse(csv);
        var errors = new ArrayList<RowMessage>();
        var warnings = new ArrayList<RowMessage>();
        Map<String, Shareholder> holders = new HashMap<>();
        shareholders.findByOrganizationIdOrderByFullNameEnAsc(orgId).forEach(h -> holders.put(key(h.getFullNameEn()), h));
        Map<String, ShareClass> byName = new HashMap<>();
        classes.findByOrganizationIdOrderBySeniorityRankDescNameAsc(orgId).forEach(c -> byName.put(key(c.getName()), c));
        int created = 0;
        for (var r : rows) {
            int n = Integer.parseInt(r.get("_row"));
            Shareholder h = holders.get(key(r.getOrDefault("holder", "")));
            ShareClass c = byName.get(key(r.getOrDefault("share_class", "")));
            if (h == null) {
                errors.add(new RowMessage(n, "Unknown holder \"" + r.getOrDefault("holder", "") + "\". Import shareholders first."));
                continue;
            }
            if (c == null) {
                errors.add(new RowMessage(n, "Unknown share class \"" + r.getOrDefault("share_class", "") + "\""));
                continue;
            }
            long qty;
            BigDecimal price;
            LocalDate date = null;
            try {
                qty = Long.parseLong(r.getOrDefault("quantity", "").replace(",", ""));
                price = new BigDecimal(r.getOrDefault("price_per_share", "").replace(",", ""));
                if (!r.getOrDefault("date", "").isEmpty()) {
                    date = LocalDate.parse(r.get("date"));
                }
            } catch (NumberFormatException | DateTimeParseException e) {
                errors.add(new RowMessage(n, "quantity must be a whole number, price_per_share a number and date YYYY-MM-DD"));
                continue;
            }
            try {
                issuance.issue(new IssueCertificateCommand(orgId, h.getId(), c.getId(), null, qty, price, null, date, user));
                created++;
            } catch (BusinessRuleException e) {
                errors.add(new RowMessage(n, e.getMessage()));
            }
        }
        return finish(orgId, "issuances", rows.size(), created, 0, errors, warnings, dryRun, user);
    }

    private Result finish(UUID orgId, String what, int rows, int created, int skipped, List<RowMessage> errors,
                          List<RowMessage> warnings, boolean dryRun, String user) {
        if (!errors.isEmpty() || dryRun) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } else {
            audit.record(orgId, "Import", orgId, AuditAction.CREATE, "imported " + created + " " + what + " from CSV (" + skipped + " skipped)", user);
        }
        return new Result(dryRun, rows, errors.isEmpty() ? created : 0, skipped, errors, warnings);
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String v) {
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
