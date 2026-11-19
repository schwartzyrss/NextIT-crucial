package com.nextit.security;

import com.nextit.exception.AppException;
import com.nextit.model.Role;
import com.nextit.model.User;
import com.nextit.model.VerificationStatus;

public final class Session {
    private static User currentUser;

    private Session() {}

    public static void login(User user) { currentUser = user; }
    public static void logout() { currentUser = null; }
    public static User currentUser() { return currentUser; }
    public static boolean isLoggedIn() { return currentUser != null; }

    public static void requireLoggedIn() {
        if (currentUser == null) {
            throw new AppException("Authentication required. Please log in.");
        }
    }

    public static void requireVerified() {
        requireLoggedIn();
        if (currentUser.getVerificationStatus() != VerificationStatus.VERIFIED) {
            VerificationStatus status = currentUser.getVerificationStatus();
            if (status == VerificationStatus.PENDING) {
                throw new AppException("Access restricted: Your identity verification is pending review.");
            } else if (status == VerificationStatus.SUSPENDED) {
                throw new AppException("Access restricted: Your account is suspended due to security or policy concerns.");
            } else if (status == VerificationStatus.REJECTED) {
                throw new AppException("Access restricted: Your verification application was rejected.");
            } else {
                throw new AppException("Access restricted: Identity verification required before accessing protected role features.");
            }
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
