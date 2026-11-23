package com.nextit.model;

import java.util.Locale;

public enum Role {
    STUDENT, INSTRUCTOR, EMPLOYER, ADMIN;

    public static Role fromString(String val) {
        if (val == null || val.isBlank()) return null;
        String normalized = val.trim().toUpperCase(Locale.ROOT);
        for (Role r : values()) {
            if (r.name().equals(normalized)) {
                return r;
            }
        }
        if (normalized.contains("STUDENT")) return STUDENT;
        if (normalized.contains("INSTRUCTOR") || normalized.contains("FACULTY")) return INSTRUCTOR;
        if (normalized.contains("EMPLOYER") || normalized.contains("INDUSTRY") || normalized.contains("COMPANY")) return EMPLOYER;
        if (normalized.contains("ADMIN")) return ADMIN;
        return null;
    }
}

