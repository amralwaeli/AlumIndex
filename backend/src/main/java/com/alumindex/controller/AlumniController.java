package com.alumindex.controller;

import com.alumindex.dto.AlumniDto;
import com.alumindex.dto.AlumniProfileDto;
import com.alumindex.security.AlumIndexPrincipal;
import com.alumindex.service.AlumniService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/alumni")
@RequiredArgsConstructor
public class AlumniController {

    private final AlumniService alumniService;

    // UC007 + UC008
    @GetMapping
    public ResponseEntity<Page<AlumniDto>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String seniority,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(alumniService.search(query, industry, seniority, page));
    }

    // UC009
    @GetMapping("/{id}/profile")
    public ResponseEntity<AlumniProfileDto> profile(@PathVariable UUID id) {
        return ResponseEntity.ok(alumniService.getProfile(id));
    }

    // UC010
    @GetMapping("/{id}/history")
    public ResponseEntity<AlumniService.HistoryView> history(@PathVariable UUID id) {
        return ResponseEntity.ok(alumniService.getHistory(id));
    }

    // UC012
    @PutMapping("/{id}/anonymise")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> anonymise(@PathVariable UUID id,
            @AuthenticationPrincipal AlumIndexPrincipal principal) {
        alumniService.anonymise(id, principal);
        return ResponseEntity.ok().build();
    }
}
