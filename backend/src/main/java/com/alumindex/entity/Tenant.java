package com.alumindex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "admin_name", nullable = false)
    private String adminName;

    @Column(name = "admin_email", nullable = false, unique = true)
    private String adminEmail;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.active;

    @Column(name = "subscription_start")
    private Instant subscriptionStart;

    @Column(name = "subscription_end")
    private Instant subscriptionEnd;

    @Builder.Default
    @Column(name = "auto_suspend_on_expiry", nullable = false)
    private boolean autoSuspendOnExpiry = true;

    @Column(name = "primary_contact")
    private String primaryContact;

    @Column(name = "contact_email")
    private String contactEmail;

    @Builder.Default
    @Column(name = "seat_limit", nullable = false)
    private int seatLimit = 5;

    @Column(name = "api_key", unique = true)
    private String apiKey;

    @Column(name = "allowed_email_domain", length = 255)
    private String allowedEmailDomain;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum SubscriptionStatus { active, suspended, expired }
}
