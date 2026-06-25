package com.alumindex.service;

import com.alumindex.common.TenantContext;
import com.alumindex.dto.AlumniDto;
import com.alumindex.dto.AlumniProfileDto;
import com.alumindex.dto.CareerEventDto;
import com.alumindex.dto.ProfileSnapshotDto;
import com.alumindex.entity.*;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.*;
import com.alumindex.security.AlumIndexPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlumniService {

    private final AlumniRepository alumniRepo;
    private final AlumniProfileRepository profileRepo;
    private final ProfileSnapshotRepository snapshotRepo;
    private final CareerEventRepository eventRepo;
    private final AuditService auditService;

    // UC007 + UC008 — Search + Filter
    @Transactional(readOnly = true)
    public Page<AlumniDto> search(String query, String industry, String seniority, int page) {
        UUID tid = requireTenantId();
        String pattern = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase() + "%";
        return alumniRepo.search(tid, pattern, industry, seniority,
                        PageRequest.of(page, 20, Sort.by("fullName")))
                .map(AlumniDto::from);
    }

    // UC009 — View profile
    @Transactional(readOnly = true)
    public AlumniProfileDto getProfile(UUID alumniId) {
        assertTenantOwns(alumniId);
        return profileRepo.findByAlumniId(alumniId)
                .map(AlumniProfileDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
    }

    // UC010 — View career history
    public record HistoryView(AlumniDto alumni, AlumniProfileDto profile,
                              List<ProfileSnapshotDto> snapshots, List<CareerEventDto> events) {}

    @Transactional(readOnly = true)
    public HistoryView getHistory(UUID alumniId) {
        Alumni alumni = assertTenantOwns(alumniId);
        return new HistoryView(
                AlumniDto.from(alumni),
                profileRepo.findByAlumniId(alumniId).map(AlumniProfileDto::from).orElse(null),
                snapshotRepo.findByAlumniIdOrderByCapturedAtAsc(alumniId).stream()
                        .map(ProfileSnapshotDto::from).toList(),
                eventRepo.findByAlumniIdOrderByDetectedAtDesc(alumniId).stream()
                        .map(CareerEventDto::from).toList());
    }

    // UC012 — Anonymise
    @Transactional
    public void anonymise(UUID alumniId, AlumIndexPrincipal actor) {
        Alumni alumni = assertTenantOwns(alumniId);
        alumni.setFullName("ANONYMISED");
        alumni.setLinkedinUrl(null);
        alumniRepo.save(alumni);
        auditService.log(actor, "ALUMNI_ANONYMISED",
                "alumniId=" + alumniId);
    }

    private UUID requireTenantId() {
        UUID id = TenantContext.get();
        if (id == null) throw new TenantAccessException("No tenant context");
        return id;
    }

    private Alumni assertTenantOwns(UUID alumniId) {
        UUID tenantId = requireTenantId();
        Alumni a = alumniRepo.findById(alumniId)
                .orElseThrow(() -> new ResourceNotFoundException("Alumni not found"));
        if (!a.getTenant().getId().equals(tenantId)) {
            throw new TenantAccessException("Cross-tenant access denied");
        }
        return a;
    }
}
