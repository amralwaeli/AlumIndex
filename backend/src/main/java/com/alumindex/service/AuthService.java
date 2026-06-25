package com.alumindex.service;

import com.alumindex.dto.LoginRequest;
import com.alumindex.dto.LoginResponse;
import com.alumindex.dto.UserDto;
import com.alumindex.entity.ActivationToken;
import com.alumindex.entity.User;
import com.alumindex.exception.BadRequestException;
import com.alumindex.exception.ResourceNotFoundException;
import com.alumindex.exception.TenantAccessException;
import com.alumindex.repository.ActivationTokenRepository;
import com.alumindex.repository.UserRepository;
import com.alumindex.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new TenantAccessException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new TenantAccessException("Invalid email or password");
        }

        if (user.getStatus() == User.Status.inactive) {
            throw new TenantAccessException("Account is inactive");
        }
        if (user.getStatus() == User.Status.pending_activation) {
            throw new TenantAccessException("Account not yet activated. Check your email for an activation link.");
        }

        var tenantId = user.getTenant() != null ? user.getTenant().getId() : null;
        String token = jwtService.generate(
                user.getId(), user.getEmail(), user.getRole().name(), tenantId);

        return new LoginResponse(token, user.getRole().name(), tenantId, UserDto.from(user));
    }

    /**
     * Returns email + institution for the activation page (validates token without consuming it).
     */
    @Transactional(readOnly = true)
    public Map<String, String> getActivationInfo(UUID tokenId) {
        ActivationToken token = activationTokenRepo.findById(tokenId)
                .orElseThrow(() -> new BadRequestException("invalid_token"));
        if (token.isUsed()) throw new BadRequestException("already_used");
        if (token.getExpiresAt().isBefore(Instant.now())) throw new BadRequestException("token_expired");

        User user = token.getUser();
        String institution = user.getTenant() != null ? user.getTenant().getInstitutionName() : "";
        return Map.of("email", user.getEmail(), "institutionName", institution);
    }

    /**
     * Sets the user's password, marks the token as used, activates the account,
     * and returns a JWT so the user is logged in immediately.
     */
    @Transactional
    public LoginResponse activateUser(UUID tokenId, String password) {
        ActivationToken token = activationTokenRepo.findById(tokenId)
                .orElseThrow(() -> new BadRequestException("invalid_token"));
        if (token.isUsed()) throw new BadRequestException("already_used");
        if (token.getExpiresAt().isBefore(Instant.now())) throw new BadRequestException("token_expired");

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(User.Status.active);
        userRepository.save(user);

        token.setUsed(true);
        activationTokenRepo.save(token);

        var tenantId = user.getTenant() != null ? user.getTenant().getId() : null;
        String jwt = jwtService.generate(user.getId(), user.getEmail(), user.getRole().name(), tenantId);
        return new LoginResponse(jwt, user.getRole().name(), tenantId, UserDto.from(user));
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("current_password_incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("same_as_current");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public UserDto updateDisplayName(UUID userId, String fullName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setFullName(fullName);
        userRepository.save(user);
        return UserDto.from(user);
    }
}
