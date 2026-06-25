package com.alumindex.repository;

import com.alumindex.entity.ProfileSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileSnapshotRepository extends JpaRepository<ProfileSnapshot, UUID> {
    List<ProfileSnapshot> findByAlumniIdOrderByCapturedAtAsc(UUID alumniId);
}
