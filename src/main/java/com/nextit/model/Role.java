package com.nextit.model;

public enum Role {
    STUDENT, INSTRUCTOR, EMPLOYER, ADMIN;

    public static Role fromString(String val) {
        if (val == null || val.isBlank()) return null;
        String normalized = val.trim().toUpperCase();
        for (Role r : values()) {
            if (r.name().equals(normalized)) {
                return r;
            }
        }
        return null;
    }
}

