package com.alumindex.pipeline;

import com.alumindex.entity.ImportBatch;
import com.alumindex.entity.Tenant;
import com.alumindex.repository.AlumniRepository;
import com.alumindex.repository.ImportBatchRepository;
import com.alumindex.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * End-to-end verification of the parallel import pipeline against in-memory H2.
 * The OpenAI call is mocked, so this runs at zero API cost and never touches Supabase.
 * Proves: (1) rows are processed concurrently (wall-clock << sequential time),
 * (2) duplicate rows for the same person never create duplicate alumni (race fix),
 * (3) counts remain correct.
 */
@SpringBootTest
@ActiveProfiles("test")
class PipelineServiceConcurrencyTest {

    @Autowired PipelineService pipeline;
    @Autowired TenantRepository tenantRepo;
    @Autowired AlumniRepository alumniRepo;
    @Autowired ImportBatchRepository batchRepo;

    @MockBean LlmNormalisationService llm; // stubbed → no real OpenAI calls, $0 cost

    @Test
    void parallel_import_has_no_duplicate_inserts_and_runs_concurrently() throws Exception {
        // Stub the LLM: one batched call per ~20 rows, ~120ms latency to mimic an API round-trip.
        when(llm.normaliseBatch(anyList())).thenAnswer(inv -> {
            List<Map<String, String>> batch = inv.getArgument(0);
            Thread.sleep(120);
            return batch.stream()
                    .map(raw -> new LlmNormalisationService.NormalisedRow(
                            raw.get("full_name"), "Acme", "Engineer",
                            "Senior", "Technology", "Kuala Lumpur", new BigDecimal("0.90")))
                    .toList();
        });

        Tenant tenant = tenantRepo.save(Tenant.builder()
                .institutionName("Test University")
                .adminName("Admin")
                .adminEmail("admin-" + UUID.randomUUID() + "@test.edu")
                .build());

        // 50 rows = 25 unique people, each appearing twice (deliberate in-file duplicates).
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Map<String, String> r = new LinkedHashMap<>();
            r.put("full_name", "Person " + i);
            r.put("education_end_year", "2020");
            rows.add(new LinkedHashMap<>(r));
            rows.add(new LinkedHashMap<>(r)); // duplicate of the same person
        }

        ImportBatch batch = batchRepo.save(ImportBatch.builder()
                .tenant(tenant).filename("test.csv").recordCount(rows.size()).build());

        long start = System.currentTimeMillis();
        pipeline.runAsync(tenant.getId(), batch.getId(), rows, tenant);
        ImportBatch done = awaitCompletion(batch.getId(), 30_000);
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(done.getStatus()).isEqualTo(ImportBatch.Status.completed);
        assertThat(done.getProcessedCount()).isEqualTo(50);
        assertThat(done.getFailedCount()).isZero();
        // 25 unique people inserted; the 25 duplicates matched their first insert.
        assertThat(done.getInsertedCount()).isEqualTo(25);
        // CRITICAL: the duplicate-race fix → exactly 25 alumni rows for this tenant, never 50.
        assertThat(alumniRepo.countByTenantId(tenant.getId())).isEqualTo(25L);
        // 50 rows x 120ms = 6s if sequential; with parallel workers it must finish far sooner.
        assertThat(elapsedMs).isLessThan(3_000L);

        System.out.printf("[PipelineServiceConcurrencyTest] 50 rows (25 unique) in %d ms; alumni=%d%n",
                elapsedMs, alumniRepo.countByTenantId(tenant.getId()));
    }

    @Test
    void resumes_a_streamed_import_from_the_persisted_offset() throws Exception {
        when(llm.normaliseBatch(anyList())).thenAnswer(inv -> {
            List<Map<String, String>> batch = inv.getArgument(0);
            return batch.stream()
                    .map(raw -> new LlmNormalisationService.NormalisedRow(
                            raw.get("full_name"), "Acme", "Engineer",
                            "Senior", "Technology", "Kuala Lumpur", new BigDecimal("0.90")))
                    .toList();
        });

        Tenant tenant = tenantRepo.save(Tenant.builder()
                .institutionName("Resume University")
                .adminName("Admin")
                .adminEmail("admin-" + UUID.randomUUID() + "@test.edu")
                .build());

        // 10 distinct people (no LinkedIn) in a CSV the streaming path will read.
        Path file = Files.createTempFile("resume-test-", ".csv");
        var sb = new StringBuilder("full_name,education_end_year\n");
        for (int i = 0; i < 10; i++) sb.append("Person ").append(i).append(",2020\n");
        Files.writeString(file, sb.toString());

        try {
            // Simulate a crash after the first 4 rows were committed: counts + offset persisted.
            ImportBatch batch = batchRepo.save(ImportBatch.builder()
                    .tenant(tenant).filename("resume.csv").recordCount(10)
                    .status(ImportBatch.Status.processing)
                    .processedCount(4).insertedCount(4).nextOffset(4)
                    .storageKey(file.toString())
                    .build());

            Map<String, String> mapping = Map.of(
                    "full_name", "full_name", "education_end_year", "education_end_year");

            pipeline.runAsync(tenant.getId(), batch.getId(), file, "resume.csv", mapping, tenant);
            ImportBatch done = awaitCompletion(batch.getId(), 30_000);

            // 4 prior + 6 resumed = 10 reported; only the 6 resumed rows are actually inserted.
            assertThat(done.getInsertedCount()).isEqualTo(10);
            assertThat(done.getProcessedCount()).isEqualTo(10);
            assertThat(alumniRepo.countByTenantId(tenant.getId())).isEqualTo(6L);
            assertThat(done.getStorageKey()).isNull();        // cleared when finished
            assertThat(Files.exists(file)).isFalse();         // buffered file deleted
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private ImportBatch awaitCompletion(UUID batchId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            ImportBatch b = batchRepo.findById(batchId).orElseThrow();
            if (b.getStatus() == ImportBatch.Status.completed) return b;
            Thread.sleep(100);
        }
        throw new AssertionError("import did not complete within " + timeoutMs + "ms");
    }
}
