package com.nextit.security;

import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.Student;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;

public final class Session {
    private static User currentUser;
    private static Student currentStudent;

    private Session() {}

    public static void login(User user) { currentUser = user; }
    public static void logout() { 
        currentUser = null; 
        currentStudent = null;
    }
    public static User currentUser() { return currentUser; }
    public static boolean isLoggedIn() { return currentUser != null; }

    public static Student currentStudent() { return currentStudent; }
    public static void setCurrentStudent(Student student) { currentStudent = student; }

    public static void updateStudentSection(String section) {
        if (currentStudent != null) {
            currentStudent.setSection(section);
        }
    }

    public static void requireLoggedIn() {
        if (currentUser == null) {
            throw new AppException("Authentication required. Please log in.");
        }
    }

    public static void requireVerified() {
        requireLoggedIn();
        if (currentUser.isSuspended()) {
            throw new AppException("Access restricted: Your account is suspended due to security or policy concerns.");
        }
    }

    public static void requireRole(Role... allowedRoles) {
        requireVerified();
        if (allowedRoles == null || allowedRoles.length == 0) return;
        for (Role r : allowedRoles) {
            if (currentUser.getRole() == r) return;
        }
        throw new AppException("Access denied: You do not have permission for this resource.");
    }
}
