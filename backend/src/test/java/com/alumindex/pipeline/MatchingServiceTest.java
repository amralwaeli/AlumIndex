package com.alumindex.pipeline;

import com.alumindex.entity.Alumni;
import com.alumindex.repository.AlumniRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * White-box branch coverage for L1 / L2 / L3 matching.
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock AlumniRepository alumniRepo;
    @InjectMocks MatchingService svc;

    UUID tid;
    Alumni alice;

    @BeforeEach
    void setUp() {
        tid   = UUID.randomUUID();
        alice = Alumni.builder()
                .id(UUID.randomUUID())
                .fullName("Alice Smith")
                .build();
    }

    // ── L1 ────────────────────────────────────────────────────────────────────

    @Test
    void l1_exact_linkedin_returns_found_with_0_97() {
        when(alumniRepo.findByLinkedinUrlAndTenantId("https://linkedin.com/in/alice", tid))
                .thenReturn(Optional.of(alice));

        var result = svc.match(tid, "https://linkedin.com/in/alice", "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.Found.class);
        assertThat(((MatchingService.Found) result).confidence()).isEqualTo(0.97);
        assertThat(((MatchingService.Found) result).alumni()).isEqualTo(alice);
    }

    @Test
    void l1_null_url_skips_to_l2() {
        when(alumniRepo.findByFullNameIgnoreCaseAndEducationEndYearAndTenantId("Alice Smith", 2020, tid))
                .thenReturn(List.of(alice));

        var result = svc.match(tid, null, "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.Found.class);
        assertThat(((MatchingService.Found) result).confidence()).isEqualTo(0.85);
    }

    @Test
    void l1_blank_url_skips_to_l2() {
        when(alumniRepo.findByFullNameIgnoreCaseAndEducationEndYearAndTenantId("Alice Smith", 2020, tid))
                .thenReturn(List.of(alice));

        var result = svc.match(tid, "   ", "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.Found.class);
    }

    // ── L2 ────────────────────────────────────────────────────────────────────

    @Test
    void l2_single_match_returns_found_with_0_85() {
        when(alumniRepo.findByLinkedinUrlAndTenantId(anyString(), any())).thenReturn(Optional.empty());
        when(alumniRepo.findByFullNameIgnoreCaseAndEducationEndYearAndTenantId("Alice Smith", 2020, tid))
                .thenReturn(List.of(alice));

        var result = svc.match(tid, "no-match-url", "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.Found.class);
        assertThat(((MatchingService.Found) result).confidence()).isEqualTo(0.85);
    }

    @Test
    void l2_multiple_matches_returns_ambiguous() {
        when(alumniRepo.findByLinkedinUrlAndTenantId(anyString(), any())).thenReturn(Optional.empty());
        when(alumniRepo.findByFullNameIgnoreCaseAndEducationEndYearAndTenantId("Alice Smith", 2020, tid))
                .thenReturn(List.of(alice, Alumni.builder().id(UUID.randomUUID()).build()));

        var result = svc.match(tid, "no-match", "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.Ambiguous.class);
    }

    @Test
    void l2_null_year_skips_to_l3() {
        when(alumniRepo.findByLinkedinUrlAndTenantId(anyString(), any())).thenReturn(Optional.empty());
        when(alumniRepo.findByFullNameIgnoreCaseAndTenantId("Alice Smith", tid))
                .thenReturn(List.of(alice));

        var result = svc.match(tid, "no-match", "Alice Smith", null);

        // null year skips L2; falls to L3 single → Found 0.65
        assertThat(result).isInstanceOf(MatchingService.Found.class);
        assertThat(((MatchingService.Found) result).confidence()).isEqualTo(0.65);
    }

    // ── L3 ────────────────────────────────────────────────────────────────────

    @Test
    void l3_single_match_returns_found_with_0_65() {
        when(alumniRepo.findByLinkedinUrlAndTenantId(anyString(), any())).thenReturn(Optional.empty());
        when(alumniRepo.findByFullNameIgnoreCaseAndEducationEndYearAndTenantId(any(), any(), any()))
                .thenReturn(List.of());
        when(alumniRepo.findByFullNameIgnoreCaseAndTenantId("Alice Smith", tid))
                .thenReturn(List.of(alice));

        var result = svc.match(tid, "no-match", "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.Found.class);
        assertThat(((MatchingService.Found) result).confidence()).isEqualTo(0.65);
    }

    @Test
    void l3_multiple_matches_returns_ambiguous() {
        when(alumniRepo.findByLinkedinUrlAndTenantId(anyString(), any())).thenReturn(Optional.empty());
        when(alumniRepo.findByFullNameIgnoreCaseAndEducationEndYearAndTenantId(any(), any(), any()))
                .thenReturn(List.of());
        when(alumniRepo.findByFullNameIgnoreCaseAndTenantId("Alice Smith", tid))
                .thenReturn(List.of(alice, Alumni.builder().id(UUID.randomUUID()).build()));

        var result = svc.match(tid, "no-match", "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.Ambiguous.class);
    }

    @Test
    void l3_no_match_returns_not_found() {
        when(alumniRepo.findByLinkedinUrlAndTenantId(anyString(), any())).thenReturn(Optional.empty());
        when(alumniRepo.findByFullNameIgnoreCaseAndEducationEndYearAndTenantId(any(), any(), any()))
                .thenReturn(List.of());
        when(alumniRepo.findByFullNameIgnoreCaseAndTenantId(any(), any()))
                .thenReturn(List.of());

        var result = svc.match(tid, "no-match", "Alice Smith", 2020);

        assertThat(result).isInstanceOf(MatchingService.NotFound.class);
    }

    @Test
    void null_name_and_null_url_returns_not_found() {
        var result = svc.match(tid, null, null, null);
        assertThat(result).isInstanceOf(MatchingService.NotFound.class);
    }

    // ── Import-time matching (LinkedIn-only — Option B) ─────────────────────────

    @Test
    void matchByLinkedin_exact_url_returns_found_with_0_97() {
        when(alumniRepo.findByLinkedinUrlAndTenantId("https://linkedin.com/in/alice", tid))
                .thenReturn(Optional.of(alice));

        var result = svc.matchByLinkedin(tid, "https://linkedin.com/in/alice");

        assertThat(result).isInstanceOf(MatchingService.Found.class);
        assertThat(((MatchingService.Found) result).confidence()).isEqualTo(0.97);
        assertThat(((MatchingService.Found) result).alumni()).isEqualTo(alice);
    }

    @Test
    void matchByLinkedin_no_url_never_consults_name_and_returns_not_found() {
        // No LinkedIn → NotFound, even though a name-based match would exist. This is the
        // Option B guarantee: distinct people sharing a name are never auto-merged.
        var result = svc.matchByLinkedin(tid, null);
        assertThat(result).isInstanceOf(MatchingService.NotFound.class);
    }

    @Test
    void matchByLinkedin_blank_url_returns_not_found() {
        var result = svc.matchByLinkedin(tid, "   ");
        assertThat(result).isInstanceOf(MatchingService.NotFound.class);
    }

    @Test
    void matchByLinkedin_unmatched_url_returns_not_found() {
        when(alumniRepo.findByLinkedinUrlAndTenantId("https://linkedin.com/in/new", tid))
                .thenReturn(Optional.empty());

        var result = svc.matchByLinkedin(tid, "https://linkedin.com/in/new");

        assertThat(result).isInstanceOf(MatchingService.NotFound.class);
    }
}
