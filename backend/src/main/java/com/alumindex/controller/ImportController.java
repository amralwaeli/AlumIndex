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

import java.util.List;
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

    // UC016 — Upload alumni data (≤ 50 MB, triggers async pipeline) → 202
    @PostMapping("/import")
    public ResponseEntity<ImportBatchDto> upload(
            @RequestParam UUID tenantId,
            @RequestPart MultipartFile file) throws Exception {

        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // Step 1 — validate file type + size, then map arbitrary headers onto
        // the canonical schema (synonyms → heuristics → LLM fallback).
        // Validation failures are user errors → 400 with the exact reason.
        List<Map<String, String>> rows;
        try {
            rows = headerMapper.mapRows(parser.parse(file).rows());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        // Create batch record (status = processing)
        ImportBatch batch = batchRepo.save(ImportBatch.builder()
                .tenant(tenant)
                .filename(file.getOriginalFilename())
                .recordCount(rows.size())
                .build());

        // Steps 2-5 run asynchronously — response is 202 with batch header
        pipeline.runAsync(tenantId, batch.getId(), rows, tenant);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportBatchDto.from(batch));
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
