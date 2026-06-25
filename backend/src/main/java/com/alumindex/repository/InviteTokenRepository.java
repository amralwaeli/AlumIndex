package com.alumindex.repository;

import com.alumindex.entity.InviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InviteTokenRepository extends JpaRepository<InviteToken, UUID> {
    Optional<InviteToken> findByToken(UUID token);
    boolean existsByEmailAndUsedFalse(String email);
    void deleteByEmailAndUsedFalse(String email);
}
