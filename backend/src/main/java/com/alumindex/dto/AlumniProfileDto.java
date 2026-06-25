package com.alumindex.dto;

import com.alumindex.entity.AlumniProfile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlumniProfileDto(
        UUID id,
        UUID alumniId,
        String employer,
        String jobTitle,
        String seniority,
        String industry,
        String location,
        BigDecimal confidenceScore,
        Instant updatedAt) {

    public static AlumniProfileDto from(AlumniProfile p) {
        if (p == null) return null;
        return new AlumniProfileDto(
                p.getId(),
                p.getAlumni().getId(),
                p.getEmployer(),
                p.getJobTitle(),
                p.getSeniority(),
                p.getIndustry(),
                p.getLocation(),
                p.getConfidenceScore(),
                p.getUpdatedAt());
    }
}
