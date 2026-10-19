package com.equigcc.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTest {

    private static List<Map<String, String>> parse(String s) {
        return Csv.parse(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsQuotedFieldsBomAndCrlf() {
        var rows = parse("﻿Name,Share Class\r\n\"Noor, Holding \"\"LLC\"\"\",Ordinary\r\n\r\nSami,\"A\nB\"\r\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("name")).isEqualTo("Noor, Holding \"LLC\"");
        assertThat(rows.get(0).get("share_class")).isEqualTo("Ordinary");
        assertThat(rows.get(1).get("share_class")).isEqualTo("A\nB");
    }

    @Test
    void rejectsEmptyFileAndUnclosedQuote() {
        assertThatThrownBy(() -> parse("")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> parse("a,b\n\"oops,1\n")).isInstanceOf(BusinessRuleException.class);
    }
}
