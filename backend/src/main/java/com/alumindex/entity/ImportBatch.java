package com.alumindex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "import_batches",
    indexes = @Index(name = "idx_import_batches_tenant_id", columnList = "tenant_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String filename;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Builder.Default
    @Column(name = "record_count", nullable = false)
    private int recordCount = 0;

    @Builder.Default
    @Column(name = "processed_count", nullable = false)
    private int processedCount = 0;

    @Builder.Default
    @Column(name = "inserted_count", nullable = false)
    private int insertedCount = 0;

    @Builder.Default
    @Column(name = "updated_count", nullable = false)
    private int updatedCount = 0;

    @Builder.Default
    @Column(name = "unchanged_count", nullable = false)
    private int unchangedCount = 0;

    @Builder.Default
    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.processing;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_log", columnDefinition = "jsonb")
    private List<Object> errorLog;

    /** Path to the buffered upload; kept until the import finishes so it can resume after a restart. */
    @Column(name = "storage_key")
    private String storageKey;

    /** Mapped-row offset of the next chunk to process; lets an interrupted import resume. */
    @Builder.Default
    @Column(name = "next_offset", nullable = false)
    private int nextOffset = 0;

    public enum Status { processing, validated, completed, failed }
}
