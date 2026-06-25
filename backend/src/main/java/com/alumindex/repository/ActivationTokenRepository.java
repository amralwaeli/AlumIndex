package com.alumindex.repository;

import com.alumindex.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {

    @Modifying
    @Query("DELETE FROM ActivationToken t WHERE t.user.id = :userId AND t.used = false")
    void deleteUnusedByUserId(@Param("userId") UUID userId);
}
