package com.alumindex.service;

import com.alumindex.common.TenantContext;
import com.alumindex.entity.Alumni;
import com.alumindex.entity.Tenant;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.*;
import com.alumindex.security.AlumIndexPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Cross-tenant access must return 403.
 */
@ExtendWith(MockitoExtension.class)
class AlumniServiceCrossTenantTest {

    @Mock AlumniRepository alumniRepo;
    @Mock AlumniProfileRepository profileRepo;
    @Mock ProfileSnapshotRepository snapshotRepo;
    @Mock CareerEventRepository eventRepo;
    @Mock AuditService auditService;
    @InjectMocks AlumniService svc;

    UUID myTenant;
    UUID otherTenant;
    Alumni foreignAlumni;

    @BeforeEach
    void setUp() {
        myTenant    = UUID.randomUUID();
        otherTenant = UUID.randomUUID();

        Tenant other = Tenant.builder().id(otherTenant).institutionName("Other Uni").build();
        foreignAlumni = Alumni.builder()
                .id(UUID.randomUUID())
                .fullName("Foreign Student")
                .tenant(other)
                .build();

        TenantContext.set(myTenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void get_profile_of_different_tenant_alumni_throws_403() {
        when(alumniRepo.findById(foreignAlumni.getId())).thenReturn(Optional.of(foreignAlumni));

        assertThatThrownBy(() -> svc.getProfile(foreignAlumni.getId()))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    void get_history_of_different_tenant_alumni_throws_403() {
        when(alumniRepo.findById(foreignAlumni.getId())).thenReturn(Optional.of(foreignAlumni));

        assertThatThrownBy(() -> svc.getHistory(foreignAlumni.getId()))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    void anonymise_different_tenant_alumni_throws_403() {
        when(alumniRepo.findById(foreignAlumni.getId())).thenReturn(Optional.of(foreignAlumni));
        var actor = new AlumIndexPrincipal(UUID.randomUUID(), "admin@my.edu", "admin", myTenant);

        assertThatThrownBy(() -> svc.anonymise(foreignAlumni.getId(), actor))
                .isInstanceOf(TenantAccessException.class);
    }
}
