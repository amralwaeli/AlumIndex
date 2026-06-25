package com.alumindex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "alumni_profiles",
    indexes = @Index(name = "idx_alumni_profiles_alumni_id", columnList = "alumni_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlumniProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumni_id", nullable = false)
    private Alumni alumni;

    private String employer;

    @Column(name = "job_title")
    private String jobTitle;

    private String seniority;

    private String industry;

    private String location;

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
