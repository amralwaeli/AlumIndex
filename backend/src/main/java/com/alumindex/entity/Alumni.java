package com.alumindex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "alumni",
    indexes = {
        @Index(name = "idx_alumni_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_alumni_linkedin_url", columnList = "linkedin_url"),
        @Index(name = "idx_alumni_name_year", columnList = "full_name, education_end_year")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Alumni {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "education_end_year")
    private Integer educationEndYear;

    @Column(name = "university_name")
    private String universityName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "alumni", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private AlumniProfile profile;
}
