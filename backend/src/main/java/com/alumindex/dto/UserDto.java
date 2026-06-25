package com.alumindex.dto;

import com.alumindex.entity.User;

import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String fullName,
        String role,
        String status,
        UUID tenantId
) {
    public static UserDto from(User u) {
        return new UserDto(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getRole().name(),
                u.getStatus().name(),
                u.getTenant() != null ? u.getTenant().getId() : null
        );
    }
}
