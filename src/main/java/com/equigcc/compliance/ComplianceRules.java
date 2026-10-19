package com.equigcc.compliance;

import com.equigcc.organization.Jurisdiction;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/**
 * The filing deadlines the product knows about, with the provision each comes from. These are a reading of the published texts, not
 * legal advice: every rule carries its basis so counsel can check it, and an unlisted rule means "not modelled", not "none".
 */
public final class ComplianceRules {

    public enum Kind { CHANGE, ANNUAL }

    public record Rule(String key, Kind kind, Set<Jurisdiction> jurisdictions, int days, boolean workingDays, int annualMonth,
                       String titleEn, String titleAr, String basis, String note) { }

    private ComplianceRules() { }

    public static final List<Rule> ALL = List.of(
            new Rule("UAE_REGISTRY_CHANGE", Kind.CHANGE, Set.of(Jurisdiction.MAINLAND_UAE), 15, true, 0,
                    "Notify the registrar of register changes", "إخطار المسجل بتغييرات السجل",
                    "Commercial Companies Law, Federal Decree-Law 32/2021, art. 15(3): 15 working days from any change to registered particulars",
                    "Applies to changes in owners and capital."),
            new Rule("UAE_UBO_CHANGE", Kind.CHANGE, Set.of(Jurisdiction.MAINLAND_UAE), 15, false, 0,
                    "Update beneficial-owner details", "تحديث بيانات المستفيد الحقيقي",
                    "Cabinet Resolution 58/2020 as amended by 109/2023: 15 days from a change (read from a secondary source; confirm with counsel)",
                    "Penalties start at a written warning, then AED 50,000 and AED 100,000 (Cabinet Resolution 132/2023)."),
            new Rule("UAE_LLC_PARTNERS_ANNUAL", Kind.ANNUAL, Set.of(Jurisdiction.MAINLAND_UAE), 0, false, 1,
                    "Send the partners register (LLC only)", "إرسال سجل الشركاء (ذ.م.م فقط)",
                    "Commercial Companies Law, Federal Decree-Law 32/2021, art. 74(3): every January",
                    "Only limited liability companies; ignore if this company is another form."),
            new Rule("DIFC_UBO_CHANGE", Kind.CHANGE, Set.of(Jurisdiction.DIFC), 30, false, 0,
                    "Update beneficial-owner details", "تحديث بيانات المستفيد الحقيقي",
                    "DIFC beneficial ownership rules: 30 days from becoming aware of a change (read from a secondary source; confirm with counsel)",
                    "Filed with the DIFC Registrar."),
            new Rule("KSA_REGISTER_FILING", Kind.CHANGE, Set.of(Jurisdiction.KSA), 15, false, 0,
                    "File the shareholders' register with the Commercial Registration Office", "تزويد مكتب السجل التجاري ببيانات سجل المساهمين",
                    "Saudi Companies Law 2022, art. 112(2): within 15 days of registration and of each amendment",
                    "Unlisted joint stock companies. The register must be kept in the Kingdom (art. 112(1))."));

    public static Set<DayOfWeek> weekend(Jurisdiction j) {
        return j == Jurisdiction.KSA ? Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY) : Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }
}
