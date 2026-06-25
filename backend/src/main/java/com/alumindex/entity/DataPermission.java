package com.alumindex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "data_permissions",
    indexes = @Index(name = "idx_data_permissions_tenant_id", columnList = "tenant_id"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "permission_key"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "permission_key", nullable = false, length = 50)
    private String permissionKey;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;
}
