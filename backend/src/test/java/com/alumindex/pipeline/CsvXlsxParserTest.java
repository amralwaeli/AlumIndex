package com.alumindex.pipeline;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the streaming parser without Spring/DB: CSV is read lazily and XLSX through the
 * POI SAX/event model (the path that replaces the OOM-prone XSSFWorkbook DOM). Header
 * validation lives in {@link HeaderMapper}; the parser accepts any columns.
 */
class CsvXlsxParserTest {

    private final CsvXlsxParser parser = new CsvXlsxParser();

    @Test
    void streams_csv_rows_with_headers() throws Exception {
        Path f = tempWith(".csv",
                "full_name,company,graduation_year\nAlice,Acme,2020\nBob,Globex,2019\n");
        try {
            var headers = new ArrayList<String>();
            var rows = new ArrayList<Map<String, String>>();
            parser.stream(f, "data.csv", headers, rows::add);

            assertThat(headers).containsExactly("full_name", "company", "graduation_year");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("full_name", "Alice").containsEntry("graduation_year", "2020");
            assertThat(rows.get(1)).containsEntry("full_name", "Bob");
            assertThat(parser.countRows(f, "data.csv")).isEqualTo(2);
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void arbitrary_columns_are_streamed_without_rejection() throws Exception {
        Path f = tempWith(".csv", "employer,job_title\nAcme,Engineer\n");
        try {
            var rows = new ArrayList<Map<String, String>>();
            parser.stream(f, "data.csv", new ArrayList<>(), rows::add);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).containsEntry("employer", "Acme");
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void empty_csv_with_only_headers_yields_no_rows() throws Exception {
        Path f = tempWith(".csv", "full_name,company,graduation_year\n");
        try {
            var rows = new ArrayList<Map<String, String>>();
            parser.stream(f, "data.csv", new ArrayList<>(), rows::add);
            assertThat(rows).isEmpty();
            assertThat(parser.countRows(f, "data.csv")).isZero();
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void streams_xlsx_via_sax() throws Exception {
        Path f = writeXlsx(2000);   // a few thousand rows via the SAX path
        try {
            var headers = new ArrayList<String>();
            var rows = new ArrayList<Map<String, String>>();
            parser.stream(f, "data.xlsx", headers, rows::add);

            assertThat(headers).containsExactly("full_name", "company", "graduation_year");
            assertThat(rows).hasSize(2000);
            assertThat(rows.get(0).get("full_name")).isEqualTo("Person 1");
            // a plain numeric cell comes back without a decimal point
            assertThat(rows.get(0).get("graduation_year")).startsWith("2001");
            assertThat(rows.get(1999).get("full_name")).isEqualTo("Person 2000");
            assertThat(parser.countRows(f, "data.xlsx")).isEqualTo(2000);
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void peek_reads_only_the_sample_rows() throws Exception {
        Path f = writeXlsx(500);
        try {
            CsvXlsxParser.Peek peek = parser.peek(f, "data.xlsx", 3);
            assertThat(peek.headers()).containsExactly("full_name", "company", "graduation_year");
            assertThat(peek.sampleRows()).hasSize(3);
            assertThat(peek.sampleRows().get(0).get("full_name")).isEqualTo("Person 1");
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void unsupported_extension_rejected() throws Exception {
        Path f = tempWith(".txt", "data");
        try {
            assertThatThrownBy(() -> parser.stream(f, "notes.txt", new ArrayList<>(), r -> { }))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
        } finally {
            Files.deleteIfExists(f);
        }
    }

    private Path tempWith(String suffix, String content) throws Exception {
        Path f = Files.createTempFile("parser-test-", suffix);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private Path writeXlsx(int dataRows) throws Exception {
        Path f = Files.createTempFile("parser-test-", ".xlsx");
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("alumni");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("full_name");
            header.createCell(1).setCellValue("company");
            header.createCell(2).setCellValue("graduation_year");
            for (int i = 1; i <= dataRows; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue("Person " + i);
                row.createCell(1).setCellValue("Acme");
                row.createCell(2).setCellValue(2000 + i);   // numeric
            }
            try (var out = Files.newOutputStream(f)) {
                wb.write(out);
            }
        }
        return f;
    }
}
