package com.nextit.repository;

import com.nextit.model.VerificationRequest;
import java.util.List;
import java.util.Optional;

public interface VerificationRequestRepository {
    Optional<VerificationRequest> findById(int id);
    Optional<VerificationRequest> findByUserId(int userId);
    List<VerificationRequest> findAll();
    List<VerificationRequest> findPending();
    void save(VerificationRequest request);
    void update(VerificationRequest request);
    void delete(int id);
}
