package com.alumindex.repository;

import com.alumindex.entity.DataPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataPermissionRepository extends JpaRepository<DataPermission, UUID> {

    List<DataPermission> findByTenantId(UUID tenantId);

    Optional<DataPermission> findByTenantIdAndPermissionKey(UUID tenantId, String permissionKey);

    void deleteByTenantId(UUID tenantId);
}
