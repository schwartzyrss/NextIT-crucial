package com.nextit.repository;

import com.nextit.model.SecurityAuditLog;
import java.util.List;

public interface SecurityAuditLogRepository {
    List<SecurityAuditLog> findAll(int limit);
    List<SecurityAuditLog> findByUserId(int userId);
    void save(SecurityAuditLog log);
}
