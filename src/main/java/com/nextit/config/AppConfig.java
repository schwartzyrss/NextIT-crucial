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
}
