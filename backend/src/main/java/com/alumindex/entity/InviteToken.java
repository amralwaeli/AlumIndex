package com.alumindex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invite_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InviteToken {

    @Id
    @Column(updatable = false, nullable = false)
    @Builder.Default
    private UUID token = UUID.randomUUID();

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String organization;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean used = false;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
