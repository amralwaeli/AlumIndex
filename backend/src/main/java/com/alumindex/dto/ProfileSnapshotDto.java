package com.alumindex.dto;

import com.alumindex.entity.ProfileSnapshot;

import java.time.Instant;
import java.util.UUID;

public record ProfileSnapshotDto(
        UUID id,
        UUID alumniId,
        Instant capturedAt,
        Object extractedFields) {

    public static ProfileSnapshotDto from(ProfileSnapshot s) {
        return new ProfileSnapshotDto(
                s.getId(),
                s.getAlumni().getId(),
                s.getCapturedAt(),
                s.getExtractedFields());
    }
}
