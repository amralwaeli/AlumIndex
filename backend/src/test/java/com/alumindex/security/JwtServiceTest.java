package com.alumindex.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test-secret-must-be-32-chars-long!!";

    JwtService svc;

    @BeforeEach
    void setUp() {
        svc = new JwtService(SECRET, 3_600_000L);
    }

    @Test
    void generate_and_parse_admin_claims() {
        UUID userId   = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = svc.generate(userId, "admin@utm.edu", "admin", tenantId);
        assertThat(token).isNotBlank();

        Claims claims = svc.parse(token);
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("role",   String.class)).isEqualTo("admin");
        assertThat(claims.get("tenantId", String.class)).isEqualTo(tenantId.toString());
    }

    @Test
    void generate_superadmin_without_tenant_stores_empty_string() {
        UUID userId = UUID.randomUUID();

        String token = svc.generate(userId, "superadmin@alumindex.app", "superadmin", null);

        Claims claims = svc.parse(token);
        assertThat(claims.get("tenantId", String.class)).isEmpty();
        assertThat(claims.get("role",     String.class)).isEqualTo("superadmin");
    }

    @Test
    void is_valid_returns_true_for_fresh_token() {
        String token = svc.generate(UUID.randomUUID(), "a@b.com", "admin", null);
        assertThat(svc.isValid(token)).isTrue();
    }

    @Test
    void tampered_token_is_invalid() {
        String token  = svc.generate(UUID.randomUUID(), "a@b.com", "admin", null);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(svc.isValid(tampered)).isFalse();
    }

    @Test
    void tampered_token_parse_throws() {
        String token  = svc.generate(UUID.randomUUID(), "a@b.com", "admin", null);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThatThrownBy(() -> svc.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expired_token_is_invalid() throws Exception {
        JwtService shortLived = new JwtService(SECRET, 1L);
        String token = shortLived.generate(UUID.randomUUID(), "a@b.com", "admin", null);
        Thread.sleep(10);
        assertThat(shortLived.isValid(token)).isFalse();
    }
}
