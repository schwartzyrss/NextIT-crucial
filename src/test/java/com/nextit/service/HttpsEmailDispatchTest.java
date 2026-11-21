package com.nextit.service;

import com.nextit.config.AppConfig;
import com.nextit.model.Role;
import com.nextit.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpsEmailDispatchTest {

    private boolean originalFallback;

    @BeforeEach
    void setUp() {
        originalFallback = AppConfig.ALLOW_OFFLINE_OTP_FALLBACK;
        AppConfig.ALLOW_OFFLINE_OTP_FALLBACK = true;
    }

    @AfterEach
    void tearDown() {
        AppConfig.ALLOW_OFFLINE_OTP_FALLBACK = originalFallback;
        System.clearProperty("nextit.allow.offline.otp.fallback");
    }

    @Test
    void supabaseUrlResolution_resolvesHttpsPort443Url() {
        String url = EmailService.getSupabaseUrl();
        assertNotNull(url);
        assertTrue(url.startsWith("https://"), "Supabase URL must use HTTPS on port 443");
        assertFalse(url.endsWith("/"), "Supabase URL should not have trailing slash");
        assertTrue(url.contains("supabase.co") || url.contains("localhost"), "URL should be valid supabase endpoint");
    }

    @Test
    void dispatchMode_parsesCorrectly() {
        EmailService.DispatchMode defaultMode = EmailService.getDispatchMode();
        assertNotNull(defaultMode);

        assertTrue(EmailService.isConfigured(), "Service should be configured via Supabase HTTPS");
    }

    @Test
    void offlineFallbackToggle_respectsAppConfigFlag() {
        String testEmail = "security-test@nextit.edu";

        // When fallback is true (default in dev/demo), master codes succeed
        AppConfig.ALLOW_OFFLINE_OTP_FALLBACK = true;
        assertTrue(AppConfig.isOfflineOtpFallbackAllowed());
        assertTrue(OtpService.verifyOtp(testEmail, "123456"));
        assertTrue(OtpService.verifyOtp(testEmail, "000000"));

        // When fallback is set to false (strict production mode), master bypass codes MUST be rejected
        AppConfig.ALLOW_OFFLINE_OTP_FALLBACK = false;
        assertFalse(AppConfig.isOfflineOtpFallbackAllowed());
        assertFalse(OtpService.verifyOtp(testEmail, "123456"), "123456 should be rejected when offline fallback is disabled");
        assertFalse(OtpService.verifyOtp(testEmail, "000000"), "000000 should be rejected when offline fallback is disabled");
    }

    @Test
    void onboardingAndRecovery_unifiedThroughHttpsPipeline() {
        // 1. Onboarding OTP flow
        OtpService.OtpResponse onboardingResp = OtpService.requestOtp("newstudent@g.bastate-u.edu.ph");
        assertNotNull(onboardingResp);
        assertNotNull(onboardingResp.code());
        assertEquals(6, onboardingResp.code().length());
        assertTrue(onboardingResp.configured());

        // 2. Password recovery OTP flow
        User user = new User();
        user.setUserId(888);
        user.setUsername("testscholar26");
        user.setEmail("testscholar26@g.bastate-u.edu.ph");
        user.setRole(Role.STUDENT);

        OtpService.OtpResponse recoveryResp = OtpService.requestPasswordResetOtp(user);
        assertNotNull(recoveryResp);
        assertNotNull(recoveryResp.code());
        assertEquals(6, recoveryResp.code().length());
        assertTrue(recoveryResp.configured());

        // Both verify cleanly with generated code or bypass when active
        assertTrue(OtpService.verifyPasswordResetOtp(user, recoveryResp.code()));
    }
}
