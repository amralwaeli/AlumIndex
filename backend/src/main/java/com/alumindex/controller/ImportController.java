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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // UC016 — Upload alumni data (triggers async streaming pipeline) → 202
    @PostMapping("/import")
    public ResponseEntity<ImportBatchDto> upload(
            @RequestParam UUID tenantId,
            @RequestPart MultipartFile file) throws Exception {

        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        String filename = file.getOriginalFilename();

        // Buffer the upload to a temp file so the async pipeline can stream it without
        // holding the whole file in heap. The pipeline deletes it when the run finishes.
        Path tmp = Files.createTempFile("alumindex-import-", ".upload");
        boolean launched = false;
        try {
            file.transferTo(tmp);

            // Pass A — peek the header + a few sample rows to resolve the schema mapping
            // and run the alumni-file validation gate. Failures are user errors → 400.
            Map<String, String> mapping;
            try {
                CsvXlsxParser.Peek peek = parser.peek(tmp, filename, 3);
                mapping = headerMapper.resolveAndValidate(peek.headers(), peek.sampleRows());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }

            int total = parser.countRows(tmp, filename);   // for the progress bar
            ImportBatch batch = batchRepo.save(ImportBatch.builder()
                    .tenant(tenant)
                    .filename(filename)
                    .recordCount(total)
                    .build());

            // Pass B — steps 2-5 stream asynchronously; response is 202 with the batch
            pipeline.runAsync(tenantId, batch.getId(), tmp, filename, mapping, tenant);
            launched = true;

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportBatchDto.from(batch));
        } finally {
            // On any failure before hand-off, clean up now (the pipeline owns the file once launched).
            if (!launched) Files.deleteIfExists(tmp);
        }
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
