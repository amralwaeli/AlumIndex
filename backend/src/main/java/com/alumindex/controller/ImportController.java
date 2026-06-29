package com.alumindex.controller;

import com.alumindex.dto.ImportBatchDto;
import com.alumindex.entity.ImportBatch;
import com.alumindex.entity.Tenant;
import com.alumindex.exception.BadRequestException;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.pipeline.CsvXlsxParser;
import com.alumindex.pipeline.HeaderMapper;
import com.alumindex.pipeline.PipelineService;
import com.alumindex.repository.ImportBatchRepository;
import com.alumindex.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class ImportController {

    private final CsvXlsxParser parser;
    private final HeaderMapper headerMapper;
    private final PipelineService pipeline;
    private final ImportBatchRepository batchRepo;
    private final TenantRepository tenantRepo;

    /** Where uploads are buffered while importing; blank → OS temp dir. */
    @Value("${alumindex.import.storage-dir:}")
    private String storageDirProp;

    // UC016 — Upload alumni data (triggers async streaming pipeline) → 202
    @PostMapping("/import")
    public ResponseEntity<ImportBatchDto> upload(
            @RequestParam UUID tenantId,
            @RequestPart MultipartFile file) throws Exception {

        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        String filename = file.getOriginalFilename();

        // Buffer the upload to the import storage dir so the async pipeline can stream it without
        // holding the whole file in heap, and resume from it if the run is interrupted. The
        // pipeline deletes it (and clears storageKey) when the run finishes.
        Path stored = Files.createTempFile(storageDir(), "import-", ".upload");
        boolean launched = false;
        try {
            file.transferTo(stored);

            // Pass A — peek the header + a few sample rows to resolve the schema mapping
            // and run the alumni-file validation gate. Failures are user errors → 400.
            Map<String, String> mapping;
            try {
                CsvXlsxParser.Peek peek = parser.peek(stored, filename, 3);
                mapping = headerMapper.resolveAndValidate(peek.headers(), peek.sampleRows());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }

            int total = parser.countRows(stored, filename);   // for the progress bar
            ImportBatch batch = batchRepo.save(ImportBatch.builder()
                    .tenant(tenant)
                    .filename(filename)
                    .recordCount(total)
                    .storageKey(stored.toString())
                    .build());

            // Pass B — steps 2-5 stream asynchronously; response is 202 with the batch
            pipeline.runAsync(tenantId, batch.getId(), stored, filename, mapping, tenant);
            launched = true;

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportBatchDto.from(batch));
        } finally {
            // On any failure before hand-off, clean up now (the pipeline owns the file once launched).
            if (!launched) Files.deleteIfExists(stored);
        }
    }

    /** Resolves (and creates) the directory where uploads are buffered during import. */
    private Path storageDir() throws IOException {
        Path dir = (storageDirProp == null || storageDirProp.isBlank())
                ? Path.of(System.getProperty("java.io.tmpdir"), "alumindex-imports")
                : Path.of(storageDirProp);
        Files.createDirectories(dir);
        return dir;
    }

    // UC005 — View batch status
    @GetMapping("/import/{batchId}")
    public ResponseEntity<ImportBatchDto> batchStatus(@PathVariable UUID batchId) {
        return batchRepo.findById(batchId)
                .map(ImportBatchDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
