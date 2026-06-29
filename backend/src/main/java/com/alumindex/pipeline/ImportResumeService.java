package com.alumindex.pipeline;

import com.alumindex.entity.ImportBatch;
import com.alumindex.repository.ImportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Resumes imports interrupted by a restart/crash. On startup, any batch still in
 * {@code processing} with its buffered upload recorded is re-launched from the persisted
 * chunk offset, so a long import survives a redeploy instead of being lost.
 *
 * Requires the buffered file to survive the restart — true on a persistent disk / mounted
 * volume (or object storage). On an ephemeral free-tier disk the file is gone after a restart,
 * so the batch is marked failed with a clear message rather than hanging in {@code processing}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportResumeService {

    private final ImportBatchRepository batchRepo;
    private final CsvXlsxParser parser;
    private final HeaderMapper headerMapper;
    private final PipelineService pipeline;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resumeInterrupted() {
        List<ImportBatch> pending = batchRepo.findResumable(ImportBatch.Status.processing);
        if (pending.isEmpty()) return;
        log.info("Found {} interrupted import(s) to resume", pending.size());

        for (ImportBatch batch : pending) {
            Path file = Path.of(batch.getStorageKey());
            if (!Files.exists(file)) {
                log.warn("Import {} cannot resume — buffered upload {} is gone after restart",
                        batch.getId(), file);
                batch.setStatus(ImportBatch.Status.failed);
                batch.setStorageKey(null);
                batch.setErrorLog(List.of(Map.of("row", "*", "code", "RESUME_FAILED",
                        "error", "The upload was lost on restart before the import finished. Please re-upload.")));
                batchRepo.save(batch);
                continue;
            }
            try {
                CsvXlsxParser.Peek peek = parser.peek(file, batch.getFilename(), 3);
                Map<String, String> mapping = headerMapper.resolveAndValidate(peek.headers(), peek.sampleRows());
                log.info("Resuming import {} ({}) from offset {}",
                        batch.getId(), batch.getFilename(), batch.getNextOffset());
                pipeline.runAsync(batch.getTenant().getId(), batch.getId(), file,
                        batch.getFilename(), mapping, batch.getTenant());
            } catch (Exception e) {
                log.error("Failed to resume import {}: {}", batch.getId(), e.getMessage(), e);
                batch.setStatus(ImportBatch.Status.failed);
                batch.setStorageKey(null);
                batchRepo.save(batch);
            }
        }
    }
}
