package com.nextit.repository;

import com.nextit.model.User;
import com.nextit.model.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(int id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    default Optional<User> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String term = identifier.trim();
        Optional<User> found = findByUsername(term);
        if (found.isEmpty()) {
            found = findByEmail(term);
        }
        return found;
    }
    List<User> findAll();
    void save(User user);
    void update(User user);
    void updatePassword(int userId, String passwordHash);
    void updateVerificationStatus(int userId, VerificationStatus status, String rejectionReason, Integer verifiedBy);
    void recordFailedLogin(int userId, int attempts, LocalDateTime lockedUntil);
    void resetFailedLogins(int userId);
    void updateMfa(int userId, boolean enabled);
    void delete(int id);
}
