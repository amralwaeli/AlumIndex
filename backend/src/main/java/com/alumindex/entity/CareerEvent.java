package com.alumindex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "career_events",
    indexes = @Index(name = "idx_career_events_alumni_id", columnList = "alumni_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CareerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumni_id", nullable = false)
    private Alumni alumni;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "significance_level", nullable = false, length = 10)
    private SignificanceLevel significanceLevel;

    @Builder.Default
    @Column(nullable = false)
    private boolean dismissed = false;

    @CreationTimestamp
    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    public enum EventType { job_change, promotion, employer_change }
    public enum SignificanceLevel { high, medium, low }
}
