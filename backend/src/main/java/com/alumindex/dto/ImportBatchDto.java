package com.alumindex.dto;

import com.alumindex.entity.ImportBatch;

import java.time.Instant;
import java.util.UUID;

public record ImportBatchDto(
        UUID id,
        UUID tenantId,
        String filename,
        Instant uploadedAt,
        Integer recordCount,
        Integer processedCount,
        Integer insertedCount,
        Integer updatedCount,
        Integer unchangedCount,
        Integer failedCount,
        String status,
        Object errorLog) {

    /** Must be called inside an open session (tenant may be lazy). */
    public static ImportBatchDto from(ImportBatch b) {
        return new ImportBatchDto(
                b.getId(),
                b.getTenant() != null ? b.getTenant().getId() : null,
                b.getFilename(),
                b.getUploadedAt(),
                b.getRecordCount(),
                b.getProcessedCount(),
                b.getInsertedCount(),
                b.getUpdatedCount(),
                b.getUnchangedCount(),
                b.getFailedCount(),
                b.getStatus() != null ? b.getStatus().name() : null,
                b.getErrorLog());
    }
}
