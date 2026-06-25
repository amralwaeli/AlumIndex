package com.alumindex.dto;

import com.alumindex.entity.CareerEvent;

import java.time.Instant;
import java.util.UUID;

public record CareerEventDto(
        UUID id,
        UUID alumniId,
        String alumniName,
        String eventType,
        String oldValue,
        String newValue,
        String significanceLevel,
        Instant detectedAt) {

    /** Must be called inside an open session (alumni may be lazy). */
    public static CareerEventDto from(CareerEvent e) {
        return new CareerEventDto(
                e.getId(),
                e.getAlumni().getId(),
                e.getAlumni().getFullName(),
                e.getEventType().name(),
                e.getOldValue(),
                e.getNewValue(),
                e.getSignificanceLevel().name(),
                e.getDetectedAt());
    }
}
