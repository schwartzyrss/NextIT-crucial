package com.nextit.config;

/**
 * Global application configuration and feature flags for NextIT.
 */
public final class AppConfig {

    private AppConfig() {}

    /**
     * Flag governing whether offline/development OTP bypass codes (e.g., 123456, 000000,
     * or locally generated test tokens) are accepted when the application cannot reach
     * Supabase over HTTPS (e.g. offline sandbox, defense testing, local dev).
     * Set to {@code false} in strict production releases to disallow master bypass codes.
     */
    public static boolean ALLOW_OFFLINE_OTP_FALLBACK = true;

    /**
     * Fallback flag that logs the generated OTP code to the console and allows
     * the UI flow to proceed to verification/login when no email API key is set or when offline.
     */
    public static boolean DEV_BYPASS_EMAIL = true;

    /**
     * Resolves whether offline OTP fallback is allowed. Checks environment variable
     * {@code NEXTIT_ALLOW_OFFLINE_OTP_FALLBACK}, system property {@code nextit.allow.offline.otp.fallback},
     * and defaults to {@link #ALLOW_OFFLINE_OTP_FALLBACK}.
     */
    public static boolean isOfflineOtpFallbackAllowed() {
        String env = System.getenv("NEXTIT_ALLOW_OFFLINE_OTP_FALLBACK");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        String prop = System.getProperty("nextit.allow.offline.otp.fallback");
        if (prop != null && !prop.isBlank()) {
            return Boolean.parseBoolean(prop.trim());
        }
        return ALLOW_OFFLINE_OTP_FALLBACK;
    }

    /**
     * Resolves whether development bypass for emails is active. Checks environment variable
     * {@code DEV_BYPASS_EMAIL}, system property {@code dev.bypass.email},
     * and defaults to {@link #DEV_BYPASS_EMAIL}.
     */
    public static boolean isDevBypassEmail() {
        String env = System.getenv("DEV_BYPASS_EMAIL");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        String prop = System.getProperty("dev.bypass.email");
        if (prop != null && !prop.isBlank()) {
            return Boolean.parseBoolean(prop.trim());
        }
        return DEV_BYPASS_EMAIL;
    }
}
