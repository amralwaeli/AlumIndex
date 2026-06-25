package com.alumindex.controller;

import com.alumindex.entity.InviteToken;
import com.alumindex.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegisterController {

    private final TenantService tenantService;

    // UC014 — validate invite token
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, String>> validateToken(@PathVariable UUID token) {
        InviteToken t = tenantService.validateToken(token);
        return ResponseEntity.ok(Map.of(
                "email", t.getEmail(),
                "organization", t.getOrganization()
        ));
    }

    // UC014 — submit registration request
    @PostMapping("/{token}")
    public ResponseEntity<Void> register(
            @PathVariable UUID token,
            @Valid @RequestBody RegisterRequest body) {
        tenantService.submitRequest(token, body.name(), body.jobTitle());
        return ResponseEntity.ok().build();
    }

    record RegisterRequest(@NotBlank String name, @NotBlank String jobTitle) {}
}
