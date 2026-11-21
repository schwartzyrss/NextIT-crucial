package com.nextit.config;

/**
 * Global application configuration and feature flags for NextIT.
 */
public final class AppConfig {

    private AppConfig() {}

    /**
     * Master toggle for Temporary Quick-Access Login (Dev / Demo Account Switcher).
     * Set to {@code false} before final release to completely hide and unmanage
     * the quick-access panel from the login interface in a single line.
     */
    public static boolean ENABLE_QUICK_LOGIN = true;

    /**
     * Flag governing whether offline/development OTP bypass codes (e.g., 123456, 000000,
     * or locally generated test tokens) are accepted when the application cannot reach
     * Supabase over HTTPS (e.g. offline sandbox, defense testing, local dev).
     * Set to {@code false} in strict production releases to disallow master bypass codes.
     */
    public static boolean ALLOW_OFFLINE_OTP_FALLBACK = true;

    /**
     * Resolves whether quick login is active. Checks environment variable
     * {@code NEXTIT_ENABLE_QUICK_LOGIN}, system property {@code nextit.enable.quick.login},
     * and defaults to {@link #ENABLE_QUICK_LOGIN}.
     */
    public static boolean isQuickLoginEnabled() {
        String env = System.getenv("NEXTIT_ENABLE_QUICK_LOGIN");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        String prop = System.getProperty("nextit.enable.quick.login");
        if (prop != null && !prop.isBlank()) {
            return Boolean.parseBoolean(prop.trim());
        }
        return ENABLE_QUICK_LOGIN;
    }

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
}
