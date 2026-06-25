package com.alumindex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
    name = "profile_snapshots",
    indexes = @Index(name = "idx_profile_snapshots_alumni_id", columnList = "alumni_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProfileSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumni_id", nullable = false)
    private Alumni alumni;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_source_data", columnDefinition = "jsonb")
    private Map<String, Object> rawSourceData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", columnDefinition = "jsonb")
    private Map<String, Object> extractedFields;
}
