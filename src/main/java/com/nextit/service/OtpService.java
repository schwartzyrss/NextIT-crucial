package com.nextit.service;

import com.nextit.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final long OTP_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    private static final long COOLDOWN_MS = 45 * 1000L;      // 45 seconds
    private static final int MAX_FAILED_ATTEMPTS = 5;

    public record OtpRecord(String code, long expiresAt, long resendAvailableAt, int[] failedAttempts) {
        public OtpRecord(String code, long expiresAt, long resendAvailableAt) {
            this(code, expiresAt, resendAvailableAt, new int[]{0});
        }
    }

    public record OtpResponse(boolean sent, boolean configured, String code, boolean rateLimited, String message) {}

    private static final Map<String, OtpRecord> ACTIVE_OTPS = new ConcurrentHashMap<>();

    private OtpService() {}

    public static OtpResponse requestOtp(String email) {
        String cleanEmail = (email != null) ? email.trim().toLowerCase() : "";
        if (cleanEmail.isBlank()) {
            return new OtpResponse(false, false, "", false, "Email address cannot be empty.");
        }

        long now = System.currentTimeMillis();
        OtpRecord existing = ACTIVE_OTPS.get(cleanEmail);
        if (existing != null && now < existing.resendAvailableAt()) {
            long waitSec = (existing.resendAvailableAt() - now) / 1000;
            return new OtpResponse(false, EmailService.isConfigured(), "", true,
                    "Please wait " + waitSec + " seconds before requesting another code.");
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        ACTIVE_OTPS.put(cleanEmail, new OtpRecord(code, now + OTP_TTL_MS, now + COOLDOWN_MS));

        EmailService.SmtpResult result = EmailService.sendOtpEmailWithStatus(cleanEmail, code);
        boolean sent = result.isSuccess();
        boolean configured = EmailService.isConfigured();

        log.info("Requested OTP for {}: sent={}, configured={}, msg={}", cleanEmail, sent, configured, result.getMessage());
        return new OtpResponse(sent, configured, code, false, result.getMessage());
    }

    public static OtpResponse requestMfaOtp(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return new OtpResponse(false, false, "", false, "User email address is missing for MFA.");
        }
        String cleanEmail = "mfa:" + user.getEmail().trim().toLowerCase();
        long now = System.currentTimeMillis();

        OtpRecord existing = ACTIVE_OTPS.get(cleanEmail);
        if (existing != null && now < existing.resendAvailableAt()) {
            long waitSec = (existing.resendAvailableAt() - now) / 1000;
            return new OtpResponse(false, EmailService.isConfigured(), "", true,
                    "Please wait " + waitSec + " seconds before requesting another MFA code.");
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        ACTIVE_OTPS.put(cleanEmail, new OtpRecord(code, now + OTP_TTL_MS, now + COOLDOWN_MS));

        EmailService.SmtpResult result = EmailService.sendOtpEmailWithStatus(user.getEmail().trim(), code);
        return new OtpResponse(result.isSuccess(), EmailService.isConfigured(), code, false, result.getMessage());
    }

    public static boolean verifyMfaOtp(User user, String enteredCode) {
        if (user == null || user.getEmail() == null || enteredCode == null) return false;
        String cleanEmail = "mfa:" + user.getEmail().trim().toLowerCase();
        return verifyOtpInternal(cleanEmail, enteredCode);
    }

    public static OtpResponse requestPasswordResetOtp(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return new OtpResponse(false, false, "", false, "No registered email address found for this account.");
        }
        String cleanKey = "reset:" + user.getUserId();
        String emailKey = "reset:" + user.getEmail().trim().toLowerCase();
        String userKey = (user.getUsername() != null) ? "reset:" + user.getUsername().trim().toLowerCase() : null;
        long now = System.currentTimeMillis();

        OtpRecord existing = ACTIVE_OTPS.get(cleanKey);
        if (existing == null) {
            existing = ACTIVE_OTPS.get(emailKey);
        }
        if (existing == null && userKey != null) {
            existing = ACTIVE_OTPS.get(userKey);
        }
        if (existing != null && now < existing.resendAvailableAt()) {
            long waitSec = (existing.resendAvailableAt() - now) / 1000;
            return new OtpResponse(false, EmailService.isConfigured(), existing.code(), true,
                    "Please wait " + waitSec + " seconds before requesting another reset code.");
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        OtpRecord rec = new OtpRecord(code, now + OTP_TTL_MS, now + COOLDOWN_MS);
        ACTIVE_OTPS.put(cleanKey, rec);
        ACTIVE_OTPS.put(emailKey, rec);
        if (userKey != null) {
            ACTIVE_OTPS.put(userKey, rec);
        }

        EmailService.SmtpResult result = EmailService.sendPasswordResetOtpEmailWithStatus(user.getEmail().trim(), code, user.getUsername());
        return new OtpResponse(result.isSuccess(), EmailService.isConfigured(), code, false, result.getMessage());
    }

    public static boolean verifyPasswordResetOtp(User user, String enteredCode) {
        if (user == null || enteredCode == null) return false;
        String cleanCode = enteredCode.replaceAll("\\D", "").trim();
        String cleanKey = "reset:" + user.getUserId();
        String emailKey = (user.getEmail() != null) ? "reset:" + user.getEmail().trim().toLowerCase() : null;
        String userKey = (user.getUsername() != null) ? "reset:" + user.getUsername().trim().toLowerCase() : null;

        if ("123456".equals(cleanCode) || "000000".equals(cleanCode)) {
            if (com.nextit.config.AppConfig.isOfflineOtpFallbackAllowed()) {
                clearPasswordResetOtp(user);
                return true;
            } else {
                log.warn("Offline bypass code rejected for reset key {}: ALLOW_OFFLINE_OTP_FALLBACK is disabled", cleanKey);
                return false;
            }
        }

        OtpRecord record = ACTIVE_OTPS.get(cleanKey);
        if (record == null && emailKey != null) {
            record = ACTIVE_OTPS.get(emailKey);
        }
        if (record == null && userKey != null) {
            record = ACTIVE_OTPS.get(userKey);
        }
        if (record == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now > record.expiresAt() || record.failedAttempts()[0] >= MAX_FAILED_ATTEMPTS) {
            clearPasswordResetOtp(user);
            return false;
        }

        if (record.code().equals(cleanCode)) {
            clearPasswordResetOtp(user);
            return true;
        }

        record.failedAttempts()[0]++;
        if (record.failedAttempts()[0] >= MAX_FAILED_ATTEMPTS) {
            clearPasswordResetOtp(user);
        }
        return false;
    }

    public static void clearPasswordResetOtp(User user) {
        if (user != null) {
            ACTIVE_OTPS.remove("reset:" + user.getUserId());
            if (user.getEmail() != null) {
                ACTIVE_OTPS.remove("reset:" + user.getEmail().trim().toLowerCase());
            }
            if (user.getUsername() != null) {
                ACTIVE_OTPS.remove("reset:" + user.getUsername().trim().toLowerCase());
            }
        }
    }

    public static boolean verifyOtp(String email, String enteredCode) {
        if (email == null || enteredCode == null) return false;
        String cleanEmail = email.trim().toLowerCase();
        return verifyOtpInternal(cleanEmail, enteredCode);
    }

    private static boolean verifyOtpInternal(String key, String enteredCode) {
        if (key == null || enteredCode == null) return false;
        String code = enteredCode.replaceAll("\\D", "").trim();
        long now = System.currentTimeMillis();

        // Universal master fallback codes for development / offline / campus Wi-Fi (gated by AppConfig)
        if ("123456".equals(code) || "000000".equals(code)) {
            if (com.nextit.config.AppConfig.isOfflineOtpFallbackAllowed()) {
                ACTIVE_OTPS.remove(key);
                return true;
            } else {
                log.warn("Offline bypass code rejected for key {}: ALLOW_OFFLINE_OTP_FALLBACK is disabled", key);
                return false;
            }
        }

        OtpRecord record = ACTIVE_OTPS.get(key);
        if (record == null) {
            return false;
        }

        if (now > record.expiresAt() || record.failedAttempts()[0] >= MAX_FAILED_ATTEMPTS) {
            ACTIVE_OTPS.remove(key);
            return false;
        }

        if (record.code().equals(code)) {
            ACTIVE_OTPS.remove(key); // Single use
            return true;
        }

        record.failedAttempts()[0]++;
        if (record.failedAttempts()[0] >= MAX_FAILED_ATTEMPTS) {
            ACTIVE_OTPS.remove(key);
        }
        return false;
    }
}
