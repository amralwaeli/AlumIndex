package com.alumindex.repository;

import com.alumindex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId")
    List<User> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND u.tenant.id = :tenantId")
    boolean existsByEmailAndTenantId(@Param("email") String email, @Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.id = :tenantId")
    long countByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.id = :tenantId AND u.role = :role AND u.status = :status")
    long countByTenantIdAndRoleAndStatus(@Param("tenantId") UUID tenantId, @Param("role") User.Role role, @Param("status") User.Status status);
}
