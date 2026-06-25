package com.alumindex.dto;

import com.alumindex.entity.Alumni;

import java.time.Instant;
import java.util.UUID;

public record AlumniDto(
        UUID id,
        UUID tenantId,
        String fullName,
        String linkedinUrl,
        Integer educationEndYear,
        String universityName,
        Instant createdAt,
        AlumniProfileDto profile) {

    /** Must be called inside an open session (profile/tenant may be lazy). */
    public static AlumniDto from(Alumni a) {
        return new AlumniDto(
                a.getId(),
                a.getTenant().getId(),
                a.getFullName(),
                a.getLinkedinUrl(),
                a.getEducationEndYear(),
                a.getUniversityName(),
                a.getCreatedAt(),
                AlumniProfileDto.from(a.getProfile()));
    }
}
