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

    /** For low-signal files the mapper asks the LLM to confirm it is alumni data. */
    private void stubClassifiedAsAlumni() {
        when(llm.classifyAlumniFile(anyList(), anyList()))
                .thenReturn(new LlmNormalisationService.FileClassification(true, "alumni", "ok"));
    }

    @Test
    void canonical_headers_pass_through_unchanged() {
        // includes 2 alumni-signal fields → classification is skipped (no LLM call)
        var out = mapper().mapRows(List.of(row(
                "full_name", "Alice Smith", "first_name", "Alice",
                "last_name", "Smith", "captured_date", "2024-01-01",
                "education_end_year", "2020", "university_name", "Tech U")));

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
    void file_with_enough_alumni_signals_skips_classification() {
        var out = mapper().mapRows(List.of(row(
                "name", "Bob", "company", "Acme", "graduation_year", "2019")));

        assertThat(out).hasSize(1);
        verify(llm, never()).classifyAlumniFile(anyList(), anyList());
    }

    @Test
    void full_name_derived_from_first_and_last() {
        stubClassifiedAsAlumni();
        var out = mapper().mapRows(List.of(row(
                "First Name", "Citra", "Surname", "Dewi")));

        assertThat(out.get(0)).containsEntry("full_name", "Citra Dewi");
    }

    @Test
    void first_and_last_derived_from_full_name() {
        stubClassifiedAsAlumni();
        var out = mapper().mapRows(List.of(row("name", "Dana Lee Wong")));

        var r = out.get(0);
        assertThat(r).containsEntry("first_name", "Dana")
                     .containsEntry("last_name", "Lee Wong");
    }

    @Test
    void missing_captured_date_defaults_to_today() {
        stubClassifiedAsAlumni();
        var out = mapper().mapRows(List.of(row("full_name", "Eve Lim")));

        assertThat(out.get(0)).containsEntry("captured_date", LocalDate.now().toString());
    }

    @Test
    void bom_prefixed_header_is_recognised() {
        stubClassifiedAsAlumni();
        var out = mapper().mapRows(List.of(row("﻿full_name", "Farah Aziz")));

        assertThat(out.get(0)).containsEntry("full_name", "Farah Aziz");
    }

    @Test
    void blank_rows_are_dropped() {
        stubClassifiedAsAlumni();
        var out = mapper().mapRows(List.of(
                row("name", "Gina Ho"),
                row("name", "  ")));

        assertThat(out).hasSize(1);
    }

    @Test
    void llm_fallback_maps_unrecognisable_headers() {
        when(llm.mapHeaders(anyList(), anyList()))
                .thenReturn(Map.of("alumno", "full_name", "empresa", "employment_company"));
        stubClassifiedAsAlumni();

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
    void non_alumni_file_is_rejected_with_clear_message() {
        when(llm.classifyAlumniFile(anyList(), anyList()))
                .thenReturn(new LlmNormalisationService.FileClassification(
                        false, "accounting", "Columns are invoice amounts, not people."));

        // "name" maps (any file can have one); amount/invoice_no are unrelated → low signal
        assertThatThrownBy(() -> mapper().mapRows(List.of(row(
                "name", "Acme Corp", "amount", "1200", "invoice_no", "INV-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alumni")
                .hasMessageContaining("accounting");
    }

    @Test
    void low_signal_file_accepted_when_classifier_unavailable() {
        // classifier down → don't block a possibly-valid import
        when(llm.classifyAlumniFile(anyList(), anyList()))
                .thenThrow(new LlmNormalisationService.LlmUnavailableException("down", null));

        var out = mapper().mapRows(List.of(row("full_name", "Iris Tan")));

        assertThat(out).hasSize(1);
    }

    @Test
    void empty_input_returns_empty_without_llm_call() {
        assertThat(mapper().mapRows(List.of())).isEmpty();
        verifyNoInteractions(llm);
    }
}
