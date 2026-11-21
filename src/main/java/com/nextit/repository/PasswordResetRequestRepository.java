package com.nextit.repository;

import com.nextit.model.PasswordResetRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRequestRepository {
    void save(PasswordResetRequest request);
    Optional<PasswordResetRequest> findById(UUID requestId);
    List<PasswordResetRequest> findByStatus(String status);
    List<PasswordResetRequest> findAll();
    void update(PasswordResetRequest request);
    void delete(UUID requestId);
}
