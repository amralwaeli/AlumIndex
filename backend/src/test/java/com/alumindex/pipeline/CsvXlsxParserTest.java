package com.alumindex.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class CsvXlsxParserTest {

    final CsvXlsxParser parser = new CsvXlsxParser();

    private static final String VALID_CSV_CONTENT =
            "full_name,first_name,last_name,captured_date\n" +
            "Alice Smith,Alice,Smith,2024-01-01\n";

    @Test
    void valid_csv_parsed_successfully() throws Exception {
        var file = new MockMultipartFile("file", "alumni.csv", "text/csv",
                VALID_CSV_CONTENT.getBytes(StandardCharsets.UTF_8));

        var result = parser.parse(file);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0)).containsEntry("full_name", "Alice Smith");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void exactly_50mb_is_accepted() throws Exception {
        long targetBytes = 50L * 1024 * 1024;
        // Build a byte array of exactly targetBytes: valid header + rows padded to exact size
        byte[] header = "full_name,first_name,last_name,captured_date\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] row = "Alice Smith,Alice,Smith,2024-01-01\n"
                .getBytes(StandardCharsets.UTF_8);

        var out = new ByteArrayOutputStream((int) targetBytes);
        out.write(header);

        long written = header.length;
        // Write complete rows until adding another would exceed the limit
        while (written + row.length <= targetBytes) {
            out.write(row);
            written += row.length;
        }
        // Pad any remaining bytes with spaces (CSV parser catches per-row errors)
        if (written < targetBytes) {
            byte[] pad = new byte[(int) (targetBytes - written)];
            Arrays.fill(pad, (byte) ' ');
            out.write(pad);
        }

        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isEqualTo((int) targetBytes);

        var file = new MockMultipartFile("file", "alumni.csv", "text/csv", bytes);
        assertThatCode(() -> parser.parse(file)).doesNotThrowAnyException();
    }

    @Test
    void file_one_byte_over_50mb_is_rejected() {
        long overLimit = 50L * 1024 * 1024 + 1;
        byte[] bytes = new byte[(int) overLimit];
        var file = new MockMultipartFile("file", "alumni.csv", "text/csv", bytes);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50 MB");
    }

    @Test
    void arbitrary_columns_are_parsed_without_rejection() throws Exception {
        // header validation moved to HeaderMapper — the parser accepts any columns
        String csv = "employer,job_title\nAcme,Engineer\n";
        var file = new MockMultipartFile("file", "alumni.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        var result = parser.parse(file);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0)).containsEntry("employer", "Acme");
    }

    @Test
    void unsupported_extension_rejected() {
        var file = new MockMultipartFile("file", "alumni.txt", "text/plain",
                "data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void empty_csv_with_valid_headers_returns_empty_rows() throws Exception {
        String csv = "full_name,first_name,last_name,captured_date\n";
        var file = new MockMultipartFile("file", "alumni.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        var result = parser.parse(file);
        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }
}
