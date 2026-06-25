package com.alumindex.repository;

import com.alumindex.entity.DashboardMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DashboardMetricRepository extends JpaRepository<DashboardMetric, UUID> {
    List<DashboardMetric> findByTenantIdOrderByGeneratedAtDesc(UUID tenantId);
}
