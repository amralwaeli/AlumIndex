package com.alumindex.service;

import com.alumindex.dto.LoginRequest;
import com.alumindex.entity.Tenant;
import com.alumindex.entity.User;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.UserRepository;
import com.alumindex.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService svc;

    Tenant tenant;
    User activeAdmin;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(UUID.randomUUID()).institutionName("UTM").build();
        activeAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@utm.edu")
                .passwordHash("hashed")
                .role(User.Role.admin)
                .status(User.Status.active)
                .tenant(tenant)
                .fullName("Admin User")
                .build();
    }

    @Test
    void successful_login_returns_token() {
        when(userRepo.findByEmail("admin@utm.edu")).thenReturn(Optional.of(activeAdmin));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(jwtService.generate(any(), any(), any(), any())).thenReturn("jwt.token.here");

        var response = svc.login(new LoginRequest("admin@utm.edu", "correct"));

        assertThat(response.token()).isEqualTo("jwt.token.here");
        assertThat(response.role()).isEqualTo("admin");
    }

    @Test
    void wrong_password_throws_403() {
        when(userRepo.findByEmail("admin@utm.edu")).thenReturn(Optional.of(activeAdmin));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> svc.login(new LoginRequest("admin@utm.edu", "wrong")))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void unknown_email_throws_403() {
        when(userRepo.findByEmail("nobody@utm.edu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.login(new LoginRequest("nobody@utm.edu", "pass")))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    void inactive_user_throws_403() {
        var inactive = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@utm.edu")
                .passwordHash("hashed")
                .role(User.Role.admin)
                .status(User.Status.inactive)
                .fullName("Inactive")
                .build();
        when(userRepo.findByEmail("inactive@utm.edu")).thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> svc.login(new LoginRequest("inactive@utm.edu", "pass")))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("inactive");
    }
}
