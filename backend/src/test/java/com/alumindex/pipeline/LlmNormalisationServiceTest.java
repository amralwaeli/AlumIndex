package com.alumindex.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * White-box tests for exponential-backoff retry logic.
 * Uses reflection to inject a mock RestTemplate.
 */
@ExtendWith(MockitoExtension.class)
class LlmNormalisationServiceTest {

    @Mock RestTemplate rest;

    LlmNormalisationService svc;

    private static final String VALID_RESPONSE = """
        {
          "choices": [{
            "message": {
              "content": "{\\"full_name\\":\\"Alice Smith\\",\\"employer\\":\\"Acme\\",\\"job_title\\":\\"Engineer\\",\\"seniority\\":\\"Senior\\",\\"industry\\":\\"Technology\\",\\"location\\":\\"KL\\",\\"confidence_score\\":\\"0.9\\"}"
            }
          }]
        }
        """;

    @BeforeEach
    void setUp() throws Exception {
        svc = new LlmNormalisationService("test-api-key", "gpt-4o-mini");
        // Inject mock RestTemplate via reflection
        var field = LlmNormalisationService.class.getDeclaredField("rest");
        field.setAccessible(true);
        field.set(svc, rest);
    }

    @Test
    void success_on_first_attempt() {
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(VALID_RESPONSE));

        var row = svc.normalise(Map.of("full_name", "Alice Smith", "captured_date", "2024-01-01"));

        assertThat(row.fullName()).isEqualTo("Alice Smith");
        assertThat(row.employer()).isEqualTo("Acme");
        assertThat(row.seniority()).isEqualTo("Senior");
        verify(rest, times(1)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void retries_on_failure_then_succeeds() {
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(ResponseEntity.ok(VALID_RESPONSE));

        // Override backoff to 0ms to keep tests fast
        var row = normaliseFast(Map.of("full_name", "Alice"));

        assertThat(row).isNotNull();
        verify(rest, times(2)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void all_retries_fail_throws_llm_unavailable() {
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> normaliseFast(Map.of("full_name", "Alice")))
                .isInstanceOf(LlmNormalisationService.LlmUnavailableException.class);

        verify(rest, times(3)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void strips_markdown_fences_from_response() {
        String fenced = """
            {
              "choices": [{
                "message": {
                  "content": "```json\\n{\\"full_name\\":\\"Bob\\",\\"employer\\":null,\\"job_title\\":null,\\"seniority\\":null,\\"industry\\":null,\\"location\\":null,\\"confidence_score\\":\\"0.5\\"}\\n```"
                }
              }]
            }
            """;
        when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(fenced));

        var row = svc.normalise(Map.of("full_name", "Bob", "captured_date", "2024-01-01"));
        assertThat(row.fullName()).isEqualTo("Bob");
    }

    // Helper that zeroes out backoff sleep time by using a short-lived spy approach
    private LlmNormalisationService.NormalisedRow normaliseFast(Map<String, String> row) {
        // Accept real backoff in tests — they're mocked so the HTTP call is instant.
        // The BACKOFF_SECONDS = {1,2,4} would make tests slow; we tolerate 1+2+4=7s
        // only on the all-fail test. For a real project, inject a clock.
        return svc.normalise(row);
    }
}
