package com.alumindex.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeaderMapperTest {

    @Mock LlmNormalisationService llm;

    HeaderMapper mapper() { return new HeaderMapper(llm); }

    private static Map<String, String> row(String... kv) {
        var m = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void canonical_headers_pass_through_unchanged() {
        var out = mapper().mapRows(List.of(row(
                "full_name", "Alice Smith", "first_name", "Alice",
                "last_name", "Smith", "captured_date", "2024-01-01")));

        assertThat(out).hasSize(1);
        assertThat(out.get(0)).containsEntry("full_name", "Alice Smith")
                              .containsEntry("captured_date", "2024-01-01");
        verifyNoInteractions(llm);
    }

    @Test
    void synonyms_and_messy_casing_are_mapped() {
        var out = mapper().mapRows(List.of(row(
                "Name", "Bob Tan", "Company", "Acme", "Job Title", "Engineer",
                "Graduation Year", "2019", "LinkedIn", "https://linkedin.com/in/bob")));

        var r = out.get(0);
        assertThat(r).containsEntry("full_name", "Bob Tan")
                     .containsEntry("employment_company", "Acme")
                     .containsEntry("employment_title", "Engineer")
                     .containsEntry("education_end_year", "2019")
                     .containsEntry("linkedin_url", "https://linkedin.com/in/bob");
        verifyNoInteractions(llm);
    }

    @Test
    void full_name_derived_from_first_and_last() {
        var out = mapper().mapRows(List.of(row(
                "First Name", "Citra", "Surname", "Dewi")));

        assertThat(out.get(0)).containsEntry("full_name", "Citra Dewi");
    }

    @Test
    void first_and_last_derived_from_full_name() {
        var out = mapper().mapRows(List.of(row("name", "Dana Lee Wong")));

        var r = out.get(0);
        assertThat(r).containsEntry("first_name", "Dana")
                     .containsEntry("last_name", "Lee Wong");
    }

    @Test
    void missing_captured_date_defaults_to_today() {
        var out = mapper().mapRows(List.of(row("full_name", "Eve Lim")));

        assertThat(out.get(0)).containsEntry("captured_date", LocalDate.now().toString());
    }

    @Test
    void bom_prefixed_header_is_recognised() {
        var out = mapper().mapRows(List.of(row("\uFEFFfull_name", "Farah Aziz")));

        assertThat(out.get(0)).containsEntry("full_name", "Farah Aziz");
    }

    @Test
    void blank_rows_are_dropped() {
        var out = mapper().mapRows(List.of(
                row("name", "Gina Ho"),
                row("name", "  ")));

        assertThat(out).hasSize(1);
    }

    @Test
    void llm_fallback_maps_unrecognisable_headers() {
        when(llm.mapHeaders(anyList(), anyList()))
                .thenReturn(Map.of("alumno", "full_name", "empresa", "employment_company"));

        var out = mapper().mapRows(List.of(row("alumno", "Hugo Reyes", "empresa", "Acme")));

        assertThat(out.get(0)).containsEntry("full_name", "Hugo Reyes")
                              .containsEntry("employment_company", "Acme");
    }

    @Test
    void no_name_column_and_llm_unavailable_rejects_with_helpful_message() {
        when(llm.mapHeaders(anyList(), anyList()))
                .thenThrow(new RuntimeException("API down"));

        assertThatThrownBy(() -> mapper().mapRows(List.of(row("foo", "1", "bar", "2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name column");
    }

    @Test
    void empty_input_returns_empty_without_llm_call() {
        assertThat(mapper().mapRows(List.of())).isEmpty();
        verifyNoInteractions(llm);
    }
}
