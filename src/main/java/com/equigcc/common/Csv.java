package com.equigcc.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal RFC 4180 reader for uploads: BOM, quoted fields with commas, quotes and line breaks, CRLF or LF. */
public final class Csv {

    public static final int MAX_ROWS = 2000;

    private Csv() {
    }

    /** Rows keyed by normalised header (lower case, spaces and dashes to underscores). Blank lines are skipped. */
    public static List<Map<String, String>> parse(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> records = records(text);
        if (records.isEmpty()) {
            throw new BusinessRuleException("The file is empty");
        }
        List<String> header = records.getFirst().stream().map(Csv::normalise).toList();
        if (records.size() - 1 > MAX_ROWS) {
            throw new BusinessRuleException("At most " + MAX_ROWS + " rows can be imported at once");
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> r = records.get(i);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), c < r.size() ? r.get(c).trim() : "");
            }
            row.put("_row", String.valueOf(i + 1));
            out.add(row);
        }
        return out;
    }

    private static String normalise(String h) {
        return h.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static List<List<String>> records(String t) {
        List<List<String>> out = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') {
                        f.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    f.append(ch);
                }
                continue;
            }
            switch (ch) {
                case '"' -> {
                    quoted = true;
                    any = true;
                }
                case ',' -> {
                    cur.add(f.toString());
                    f.setLength(0);
                    any = true;
                }
                case '\r' -> {
                }
                case '\n' -> {
                    if (any || f.length() > 0) {
                        cur.add(f.toString());
                        out.add(cur);
                    }
                    cur = new ArrayList<>();
                    f.setLength(0);
                    any = false;
                }
                default -> {
                    f.append(ch);
                    any = true;
                }
            }
        }
        if (quoted) {
            throw new BusinessRuleException("The file has an unclosed quote");
        }
        if (any || f.length() > 0) {
            cur.add(f.toString());
            out.add(cur);
        }
        return out;
    }
}
