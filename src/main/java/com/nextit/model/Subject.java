package com.nextit.model;

public class Subject {
    private int subjectId;
    private String subjectCode;
    private String subjectName;
    private double units;
    private String description;
    private Integer instructorId;
    private String section;
    private String semester;
    private String academicYear;

    public int getSubjectId() { return subjectId; }
    public void setSubjectId(int subjectId) { this.subjectId = subjectId; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getSubjectTitle() { return subjectName; }
    public void setSubjectTitle(String subjectTitle) { this.subjectName = subjectTitle; }

    public double getUnits() { return units; }
    public void setUnits(double units) { this.units = units; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getInstructorId() { return instructorId; }
    public void setInstructorId(Integer instructorId) { this.instructorId = instructorId; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    @Override
    public String toString() {
        return (subjectCode != null ? subjectCode : "") + " - " + (subjectName != null ? subjectName : "");
    }
}

