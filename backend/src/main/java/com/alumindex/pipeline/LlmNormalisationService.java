package com.alumindex.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI gpt-4o-mini to normalise one raw CSV row into structured alumni fields.
 * Retry: exponential backoff 1 → 2 → 4 s (3 attempts). HTTP 429/5xx are treated as
 * transient and honour the Retry-After header so brief rate-limit bursts (common
 * when the import runs many rows concurrently) recover instead of tripping the
 * pipeline's circuit breaker and degrading to fallback normalisation.
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
     * Normalises several rows in a SINGLE OpenAI call (returns a JSON array), cutting the number
     * of round-trips by ~{@code rows.size()}×. The returned list is aligned by index with the input;
     * an element is {@code null} if the model omitted/garbled that record, so the caller can fall
     * back to deterministic normalisation for just that row. Throws {@link LlmUnavailableException}
     * (via {@link #withRetries}) only when OpenAI itself is unreachable.
     */
    public List<NormalisedRow> normaliseBatch(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        if (rows.size() == 1) {
            return java.util.Collections.singletonList(normalise(rows.get(0)));
        }
        return parseBatchResponse(withRetries(buildBatchPrompt(rows)), rows.size());
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

    /** Outcome of the file-type sanity check. */
    public record FileClassification(boolean isAlumni, String detectedType, String reason) {}

    /**
     * Confirms an uploaded file actually describes alumni/graduates rather than an
     * unrelated dataset (accounting, invoices, inventory, generic contacts, …).
     * Used only for low-signal files where header mapping alone is inconclusive.
     */
    public FileClassification classifyAlumniFile(List<String> headers,
                                                 List<Map<String, String>> sampleRows) {
        String prompt = """
            You validate files uploaded to a university ALUMNI management system.
            An alumni file lists individual people (graduates), each with details such
            as their name, graduation year, university, degree, employer, job title or
            LinkedIn. Files about other domains — accounting, invoices, sales,
            inventory, products, or generic contact lists — are NOT alumni files.
            Decide which this is from the headers and sample rows.
            Return ONLY JSON: {"is_alumni": true|false, "detected_type": "<short label>", "reason": "<one short sentence>"}.
            Headers: %s
            Sample rows: %s
            """.formatted(headers, sampleRows);
        try {
            JsonNode data = new ObjectMapper().readTree(withRetries(prompt));
            return new FileClassification(
                    data.path("is_alumni").asBoolean(false),
                    data.path("detected_type").asText(""),
                    data.path("reason").asText(""));
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException("Failed to classify file: " + e.getMessage(), e);
        }
    }

    /** Runs one prompt with exponential backoff and returns the message content (fences stripped). */
    private String withRetries(String prompt) {
        Exception lastEx = null;
        for (int attempt = 0; attempt < BACKOFF_SECONDS.length; attempt++) {
            try {
                return callOpenAi(prompt);
            } catch (RateLimitException e) {
                lastEx = e;
                long wait = Math.max(BACKOFF_SECONDS[attempt], e.retryAfterSeconds);
                log.warn("OpenAI rate-limited (HTTP {}) attempt {}/{} — waiting {}s",
                        e.code, attempt + 1, BACKOFF_SECONDS.length, wait);
                if (attempt < BACKOFF_SECONDS.length - 1) {
                    sleep((int) wait);
                }
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

        ResponseEntity<String> response;
        try {
            response = rest.exchange(
                    OPENAI_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException e) {
            int code = e.getStatusCode().value();
            // 429 (rate limit) and 5xx are transient → retry rather than fail the row
            if (code == 429 || code >= 500) {
                throw new RateLimitException(code, parseRetryAfter(e));
            }
            throw e; // 4xx like 401/403 are persistent (bad key/quota) → let it bubble
        }

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
            return toNormalised(new ObjectMapper().readTree(content));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    /** Parses a JSON array of N normalised objects; index-aligned with input, null per bad element. */
    private List<NormalisedRow> parseBatchResponse(String content, int expected) {
        var out = new java.util.ArrayList<NormalisedRow>(expected);
        JsonNode arr;
        try {
            arr = new ObjectMapper().readTree(content);
        } catch (Exception e) {
            for (int i = 0; i < expected; i++) out.add(null);   // whole batch unparseable → fall back per row
            return out;
        }
        for (int i = 0; i < expected; i++) {
            JsonNode node = arr.isArray() && i < arr.size() ? arr.get(i) : null;
            out.add(node != null && node.isObject() ? toNormalised(node) : null);
        }
        return out;
    }

    private static NormalisedRow toNormalised(JsonNode data) {
        BigDecimal confidence;
        try {
            confidence = new BigDecimal(data.path("confidence_score").asText("0.7"));
        } catch (NumberFormatException e) {
            confidence = new BigDecimal("0.7");
        }
        return new NormalisedRow(
                text(data, "full_name"),
                text(data, "employer"),
                text(data, "job_title"),
                text(data, "seniority"),
                text(data, "industry"),
                text(data, "location"),
                confidence);
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

    /** One prompt that normalises N records and asks for a JSON array, one object per record in order. */
    private static String buildBatchPrompt(List<Map<String, String>> rows) {
        String recordsJson;
        try {
            recordsJson = new ObjectMapper().writeValueAsString(rows);
        } catch (Exception e) {
            recordsJson = rows.toString();
        }
        return """
            Normalise EACH of the following alumni records. Return ONLY a JSON array containing exactly
            one object per input record, in the SAME ORDER, each with these fields:
            full_name, employer, job_title, seniority (one of: Junior, Mid-Level, Senior, Lead, Manager, Director, VP, C-Suite),
            industry (e.g. Technology, Finance, Healthcare, Energy, etc.), location (city/country), confidence_score (0.0-1.0).
            Strip titles from full_name (Dr., Prof., Mr., Mrs., etc.).
            The array length MUST equal the number of input records.
            Records (JSON array):
            """ + recordsJson;
    }

    private static void sleep(int seconds) {
        try { Thread.sleep(seconds * 1000L); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    /** Transient OpenAI failure (HTTP 429 / 5xx) — retried with backoff, not surfaced as terminal. */
    private static class RateLimitException extends RuntimeException {
        final int code;
        final long retryAfterSeconds;
        RateLimitException(int code, long retryAfterSeconds) {
            super("OpenAI HTTP " + code);
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    /** Reads the Retry-After response header (seconds); 0 if absent or unparseable. */
    private static long parseRetryAfter(HttpStatusCodeException e) {
        var headers = e.getResponseHeaders();
        if (headers != null) {
            String ra = headers.getFirst("Retry-After");
            if (ra != null) {
                try { return Long.parseLong(ra.trim()); } catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }
}
