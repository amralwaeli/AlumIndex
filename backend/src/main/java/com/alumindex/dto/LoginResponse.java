package com.alumindex.dto;

import java.util.UUID;

public record LoginResponse(
        String token,
        String role,
        UUID tenantId,
        UserDto user
) {}
