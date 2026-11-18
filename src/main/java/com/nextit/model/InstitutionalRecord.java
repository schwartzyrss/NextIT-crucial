package com.nextit.model;

import java.time.LocalDateTime;

public class InstitutionalRecord {
    private int recordId;
    private Role recordType;
    private String identifierNumber;
    private String institutionalEmail;
    private String fullName;
    private String departmentOrProgram;
    private boolean active = true;
    private LocalDateTime createdAt;

    public int getRecordId() { return recordId; }
    public void setRecordId(int recordId) { this.recordId = recordId; }

    public Role getRecordType() { return recordType; }
    public void setRecordType(Role recordType) { this.recordType = recordType; }

    public String getIdentifierNumber() { return identifierNumber; }
    public void setIdentifierNumber(String identifierNumber) { this.identifierNumber = identifierNumber; }

    public String getInstitutionalEmail() { return institutionalEmail; }
    public void setInstitutionalEmail(String institutionalEmail) { this.institutionalEmail = institutionalEmail; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getDepartmentOrProgram() { return departmentOrProgram; }
    public void setDepartmentOrProgram(String departmentOrProgram) { this.departmentOrProgram = departmentOrProgram; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
