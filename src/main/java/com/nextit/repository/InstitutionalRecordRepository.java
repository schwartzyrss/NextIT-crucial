package com.nextit.repository;

import com.nextit.model.InstitutionalRecord;
import java.util.List;
import java.util.Optional;

public interface InstitutionalRecordRepository {
    Optional<InstitutionalRecord> findById(int id);
    Optional<InstitutionalRecord> findByIdentifier(String identifierNumber);
    Optional<InstitutionalRecord> findByEmail(String institutionalEmail);
    List<InstitutionalRecord> findAll();
    void save(InstitutionalRecord record);
    void update(InstitutionalRecord record);
    void delete(int id);
}
