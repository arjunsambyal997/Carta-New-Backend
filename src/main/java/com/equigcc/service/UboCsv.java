package com.equigcc.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** CSV rendering of the UBO schedule: UTF-8 with BOM so Excel reads Arabic, RFC 4180 quoting, formula injection neutralised. */
final class UboCsv {

    private UboCsv() {
    }

    static byte[] render(UboReport r) {
        var sb = new StringBuilder("﻿");
        row(sb, List.of("Section", "Name (EN)", "Name (AR)", "Nationality", "Document no.", "Economic %", "Voting %",
                "Qualifies by", "Holding", "Route"));
        for (var p : r.beneficialOwners()) {
            person(sb, "BENEFICIAL_OWNER", p);
        }
        for (var p : r.belowThreshold()) {
            person(sb, "BELOW_THRESHOLD", p);
        }
        for (var g : r.gaps()) {
            row(sb, List.of("UNRESOLVED", g.nameEn(), nz(g.nameAr()), "", "", g.economicPct().toPlainString(),
                    g.votingPct().toPlainString(), "", "", g.reason()));
        }
        for (String w : r.warnings()) {
            row(sb, List.of("WARNING", "", "", "", "", "", "", "", "", w));
        }
        row(sb, List.of("NOTE", "", "", "", "", "", "", "", "", r.disclaimer()));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void person(StringBuilder sb, String section, UboReport.Person p) {
        List<String> routes = new ArrayList<>();
        for (var path : p.paths()) {
            routes.add(String.join(" > ", path.chain()) + " (" + path.economicPct().toPlainString() + "% / "
                    + path.votingPct().toPlainString() + "%)");
        }
        row(sb, List.of(section, p.nameEn(), nz(p.nameAr()), nz(p.nationality()), nz(p.documentNo()),
                p.economicPct().toPlainString(), p.votingPct().toPlainString(), nz(p.qualifiesBy()),
                p.direct() ? "Direct and/or indirect" : "Indirect", String.join("; ", routes)));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static void row(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
    }

    static String cell(String v) {
        String s = v;
        // Spreadsheet apps execute cells that begin with these characters as formulas.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
