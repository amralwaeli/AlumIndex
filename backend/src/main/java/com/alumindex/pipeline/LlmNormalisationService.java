package com.alumindex.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI gpt-4o-mini to normalise one raw CSV row into structured alumni fields.
 * Retry: exponential backoff 1 s → 2 s → 4 s (3 attempts).
 * On all-retry failure the row is marked failed and the pipeline continues.
 */
@Service
@Slf4j
public class LlmNormalisationService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int[] BACKOFF_SECONDS = {1, 2, 4};

    private final RestTemplate rest;
    private final String apiKey;
    private final String model;

    public LlmNormalisationService(
            @Value("${alumindex.openai.api-key}") String apiKey,
            @Value("${alumindex.openai.model:gpt-4o-mini}") String model) {
        this.rest   = new RestTemplate();
        this.apiKey = apiKey;
        this.model  = model;
    }

    public record NormalisedRow(
            String fullName,
            String employer,
            String jobTitle,
            String seniority,
            String industry,
            String location,
            BigDecimal confidenceScore
    ) {}

    public NormalisedRow normalise(Map<String, String> rawRow) {
        return parseResponse(withRetries(buildPrompt(rawRow)));
    }

    /**
     * Asks the LLM to map arbitrary CSV headers onto the canonical schema.
     * Returns originalHeader → canonicalField; headers the model can't place
     * are omitted. Used as a fallback when synonym matching finds no name column.
     */
    public Map<String, String> mapHeaders(List<String> headers,
                                          List<Map<String, String>> sampleRows) {
        String prompt = """
            Map each CSV header below to exactly one of these canonical fields, or null if none fits:
            full_name, first_name, last_name, captured_date, linkedin_url, employment_title,
            employment_company, company_industry, company_size, company_type, location_city,
            location_state, location_country, education_degree, education_major,
            education_end_year, university_name, employment_start_month, employment_start_year.
            Each canonical field may be used at most once.
            Return ONLY a JSON object of the form {"<original header>": "<canonical field or null>"}.
            Headers: %s
            Sample rows: %s
            """.formatted(headers, sampleRows);

        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode data = om.readTree(withRetries(prompt));
            var mapping = new LinkedHashMap<String, String>();
            data.fields().forEachRemaining(e -> {
                String canon = e.getValue().isNull() ? null : e.getValue().asText();
                if (canon != null && !canon.isBlank() && !"null".equals(canon)) {
                    mapping.put(e.getKey(), canon);
                }
            });
            return mapping;
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException("Failed to parse header mapping: " + e.getMessage(), e);
        }
    }

    /** Runs one prompt with exponential backoff and returns the message content (fences stripped). */
    private String withRetries(String prompt) {
        Exception lastEx = null;
        for (int attempt = 0; attempt < BACKOFF_SECONDS.length; attempt++) {
            try {
                return callOpenAi(prompt);
            } catch (Exception e) {
                lastEx = e;
                log.warn("OpenAI attempt {}/{} failed: {}", attempt + 1, BACKOFF_SECONDS.length,
                        e.getMessage());
                if (attempt < BACKOFF_SECONDS.length - 1) {
                    sleep(BACKOFF_SECONDS[attempt]);
                }
            }
        }
        throw new LlmUnavailableException("OpenAI API unavailable after retries", lastEx);
    }

    private String callOpenAi(String prompt) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        var body = Map.of(
                "model", model,
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are a data normalisation assistant. Return ONLY valid JSON."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        ResponseEntity<String> response = rest.exchange(
                OPENAI_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        ObjectMapper om = new ObjectMapper();
        try {
            JsonNode root = om.readTree(response.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText();
            // Strip markdown fences if present
            return content.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read OpenAI response: " + e.getMessage(), e);
        }
    }

    private NormalisedRow parseResponse(String content) {
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode data = om.readTree(content);
            return new NormalisedRow(
                    text(data, "full_name"),
                    text(data, "employer"),
                    text(data, "job_title"),
                    text(data, "seniority"),
                    text(data, "industry"),
                    text(data, "location"),
                    new BigDecimal(data.path("confidence_score").asText("0.7"))
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode n, String field) {
        return n.has(field) && !n.path(field).isNull() ? n.path(field).asText("") : null;
    }

    private static String buildPrompt(Map<String, String> row) {
        return """
            Normalise the following alumni record and return a JSON object with exactly these fields:
            full_name, employer, job_title, seniority (one of: Junior, Mid-Level, Senior, Lead, Manager, Director, VP, C-Suite),
            industry (e.g. Technology, Finance, Healthcare, Energy, etc.), location (city/country), confidence_score (0.0-1.0).
            Strip titles from full_name (Dr., Prof., Mr., Mrs., etc.).
            Raw record:
            """ + row.toString();
    }

    private static void sleep(int seconds) {
        try { Thread.sleep(seconds * 1000L); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }
}
