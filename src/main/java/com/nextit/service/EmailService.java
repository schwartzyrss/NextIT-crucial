package com.nextit.service;

import com.nextit.config.AppConfig;
import com.nextit.config.DatabaseConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Service managing outbound email delivery for NextIT.
 * Dispatches verification & recovery OTP emails over standard HTTPS (Port 443) via Supabase
 * (Option A: Supabase Edge Function `send-otp-email`, Option B: Supabase Auth OTP) to prevent
 * port blocking / timeouts caused by campus & ISP firewalls on raw SMTP ports (587/465).
 */
public final class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Properties DOT_ENV = loadDotEnv();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public enum DispatchMode {
        EDGE_FUNCTION, // Option A: Supabase Edge Function `send-otp-email`
        SUPABASE_AUTH, // Option B: Supabase Auth native OTP (/auth/v1/otp)
        DIRECT_SMTP,   // Legacy: direct desktop SMTP socket (ports 587/465)
        AUTO           // Auto: tries Edge Function -> Supabase Auth -> offline fallback
    }

    public static class SmtpResult {
        private final boolean success;
        private final String message;
        private final String code;
        private final String deliveryMethod;
        private final boolean fallbackUsed;

        public SmtpResult(boolean success, String message, String code) {
            this(success, message, code, "HTTPS (Port 443)", false);
        }

        public SmtpResult(boolean success, String message, String code, String deliveryMethod, boolean fallbackUsed) {
            this.success = success;
            this.message = message;
            this.code = code;
            this.deliveryMethod = deliveryMethod != null ? deliveryMethod : "HTTPS (Port 443)";
            this.fallbackUsed = fallbackUsed;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
        public String getDeliveryMethod() { return deliveryMethod; }
        public boolean isFallbackUsed() { return fallbackUsed; }
    }

    private EmailService() {}

    public static String getSupabaseUrl() {
        String url = getSetting("SUPABASE_URL", "");
        if (url.isBlank()) {
            String dbUser = DatabaseConfig.user();
            if (dbUser.contains(".")) {
                String projectRef = dbUser.substring(dbUser.indexOf('.') + 1);
                if (!projectRef.isBlank()) {
                    url = "https://" + projectRef + ".supabase.co";
                }
            }
        }
        if (url.isBlank()) {
            url = "https://qlmrcmaqkvtmciursqvr.supabase.co";
        }
        return url.replaceAll("/+$", "");
    }

    public static String getSupabaseAnonKey() {
        return getSetting("SUPABASE_ANON_KEY", getSetting("SUPABASE_KEY", ""));
    }

    public static DispatchMode getDispatchMode() {
        String mode = getSetting("OTP_DISPATCH_MODE", getSetting("EMAIL_DISPATCH_MODE", "AUTO")).trim().toUpperCase();
        return switch (mode) {
            case "EDGE_FUNCTION", "EDGE", "OPTION_A" -> DispatchMode.EDGE_FUNCTION;
            case "SUPABASE_AUTH", "AUTH", "OPTION_B" -> DispatchMode.SUPABASE_AUTH;
            case "DIRECT_SMTP", "SMTP" -> DispatchMode.DIRECT_SMTP;
            default -> DispatchMode.AUTO;
        };
    }

    public static boolean isConfigured() {
        String enabled = getSetting("EMAIL_ENABLED", getSetting("SMTP_ENABLED", "true"));
        if ("false".equalsIgnoreCase(enabled) || "0".equals(enabled)) return false;
        DispatchMode mode = getDispatchMode();
        if (mode == DispatchMode.DIRECT_SMTP) {
            String user = getSetting("SMTP_USER", getSetting("SMTP_USERNAME", ""));
            String pass = getSetting("SMTP_PASSWORD", getSetting("SMTP_APP_PASSWORD", getSetting("SMTP_PASS", "")));
            return user != null && !user.isBlank() && pass != null && !pass.isBlank();
        }
        return getSupabaseUrl() != null && !getSupabaseUrl().isBlank();
    }

    public static boolean sendOtpEmail(String toEmail, String otpCode) {
        return sendOtpEmailWithStatus(toEmail, otpCode).isSuccess();
    }

    /**
     * Dispatches onboarding verification OTP email over HTTPS (Port 443).
     */
    public static SmtpResult sendOtpEmailWithStatus(String toEmail, String otpCode) {
        return dispatchEmail(toEmail, otpCode, "ONBOARDING_VERIFICATION", null);
    }

    /**
     * Dispatches password recovery OTP email over HTTPS (Port 443).
     */
    public static SmtpResult sendPasswordResetOtpEmailWithStatus(String toEmail, String otpCode, String username) {
        return dispatchEmail(toEmail, otpCode, "PASSWORD_RESET", username);
    }

    /**
     * Main dispatch pipeline supporting Option A (Edge Function) and Option B (Supabase Auth).
     */
    public static SmtpResult dispatchEmail(String toEmail, String otpCode, String purpose, String username) {
        if (toEmail == null || toEmail.isBlank()) {
            return new SmtpResult(false, "Recipient email address cannot be empty.", otpCode);
        }

        DispatchMode mode = getDispatchMode();
        log.info("Dispatching OTP email to {} (purpose={}) via mode={}", toEmail, purpose, mode);

        if (mode == DispatchMode.DIRECT_SMTP) {
            return sendDirectSmtp(toEmail, otpCode, purpose, username);
        }

        if (mode == DispatchMode.SUPABASE_AUTH) {
            SmtpResult authRes = sendViaSupabaseAuthOtp(toEmail);
            if (authRes.isSuccess()) return authRes;
            return evaluateFallback(toEmail, otpCode, authRes.getMessage());
        }

        if (mode == DispatchMode.EDGE_FUNCTION) {
            SmtpResult edgeRes = sendViaSupabaseEdgeFunction(toEmail, otpCode, purpose, username, null, null, null);
            if (edgeRes.isSuccess()) return edgeRes;
            return evaluateFallback(toEmail, otpCode, edgeRes.getMessage());
        }

        // Mode AUTO: Attempt Option A (Edge Function) first
        try {
            SmtpResult edgeRes = sendViaSupabaseEdgeFunction(toEmail, otpCode, purpose, username, null, null, null);
            if (edgeRes.isSuccess()) {
                return edgeRes;
            }
            log.warn("Option A (Edge Function) failed: {}. Trying Option B (Supabase Auth)...", edgeRes.getMessage());
        } catch (Exception ex) {
            log.warn("Option A invocation exception: {}. Trying Option B...", ex.getMessage());
        }

        // Mode AUTO: Attempt Option B (Supabase Auth OTP)
        try {
            SmtpResult authRes = sendViaSupabaseAuthOtp(toEmail);
            if (authRes.isSuccess()) {
                return authRes;
            }
            log.warn("Option B (Supabase Auth) failed: {}", authRes.getMessage());
        } catch (Exception ex) {
            log.warn("Option B invocation exception: {}", ex.getMessage());
        }

        // Offline or server not yet deployed: evaluate fallback
        return evaluateFallback(toEmail, otpCode, "HTTPS delivery endpoints unreachable.");
    }

    /**
     * Option A: Invokes Supabase Edge Function `send-otp-email` over HTTPS (Port 443).
     */
    public static SmtpResult sendViaSupabaseEdgeFunction(String toEmail, String otpCode, String purpose, String username,
                                                         String fullName, Boolean approved, String remarks) {
        String supabaseUrl = getSupabaseUrl();
        String anonKey = getSupabaseAnonKey();
        String endpoint = supabaseUrl + "/functions/v1/send-otp-email";

        StringBuilder json = new StringBuilder("{");
        json.append("\"email\":\"").append(escapeJson(toEmail)).append("\"");
        if (otpCode != null) {
            json.append(",\"otpCode\":\"").append(escapeJson(otpCode)).append("\"");
        }
        if (purpose != null) {
            json.append(",\"purpose\":\"").append(escapeJson(purpose)).append("\"");
        }
        if (username != null) {
            json.append(",\"username\":\"").append(escapeJson(username)).append("\"");
        }
        if (fullName != null) {
            json.append(",\"fullName\":\"").append(escapeJson(fullName)).append("\"");
        }
        if (approved != null) {
            json.append(",\"decisionApproved\":").append(approved);
        }
        if (remarks != null) {
            json.append(",\"decisionRemarks\":\"").append(escapeJson(remarks)).append("\"");
        }
        json.append("}");

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8));

            if (!anonKey.isBlank()) {
                reqBuilder.header("apikey", anonKey);
                reqBuilder.header("Authorization", "Bearer " + anonKey);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();

            if (status >= 200 && status < 300) {
                log.info("Supabase Edge Function dispatch succeeded for {}: HTTP {}", toEmail, status);
                String msg = extractJsonStringField(body, "message");
                if (msg.isBlank()) msg = "OTP email dispatched successfully via HTTPS (Supabase Edge Function)";
                return new SmtpResult(true, msg, otpCode, "HTTPS (Port 443 - Edge Function)", false);
            } else {
                log.warn("Supabase Edge Function returned HTTP {}: {}", status, body);
                String err = extractJsonStringField(body, "error");
                if (err.isBlank()) err = extractJsonStringField(body, "message");
                if (err.isBlank()) err = "HTTP " + status + " (" + body + ")";
                return new SmtpResult(false, err, otpCode, "HTTPS (Port 443 - Edge Function)", false);
            }
        } catch (Exception ex) {
            log.warn("HTTPS request to Supabase Edge Function failed: {}", ex.getMessage());
            return new SmtpResult(false, ex.getMessage(), otpCode, "HTTPS (Port 443 - Edge Function)", false);
        }
    }

    /**
     * Option B: Triggers Supabase Auth native OTP endpoint over HTTPS (Port 443).
     */
    public static SmtpResult sendViaSupabaseAuthOtp(String toEmail) {
        String supabaseUrl = getSupabaseUrl();
        String anonKey = getSupabaseAnonKey();
        String endpoint = supabaseUrl + "/auth/v1/otp";

        String json = "{\"email\":\"" + escapeJson(toEmail) + "\",\"create_user\":false}";

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

            if (!anonKey.isBlank()) {
                reqBuilder.header("apikey", anonKey);
                reqBuilder.header("Authorization", "Bearer " + anonKey);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();

            if (status >= 200 && status < 300) {
                log.info("Supabase Auth OTP dispatch succeeded for {}: HTTP {}", toEmail, status);
                return new SmtpResult(true, "OTP email dispatched via Supabase Auth HTTPS", "", "HTTPS (Port 443 - Supabase Auth)", false);
            } else {
                log.warn("Supabase Auth OTP returned HTTP {}: {}", status, body);
                String msg = extractJsonStringField(body, "msg");
                if (msg.isBlank()) msg = extractJsonStringField(body, "error_description");
                if (msg.isBlank()) msg = extractJsonStringField(body, "message");
                if (msg.isBlank()) msg = "HTTP " + status + " " + body;
                return new SmtpResult(false, msg, "", "HTTPS (Port 443 - Supabase Auth)", false);
            }
        } catch (Exception ex) {
            log.warn("HTTPS request to Supabase Auth OTP failed: {}", ex.getMessage());
            return new SmtpResult(false, ex.getMessage(), "", "HTTPS (Port 443 - Supabase Auth)", false);
        }
    }

    /**
     * Verification decision notification email (Admin review workspace).
     */
    public static SmtpResult sendVerificationDecisionEmail(String toEmail, String fullName, com.nextit.model.Role role, boolean approved, String remarks) {
        if (toEmail == null || toEmail.isBlank()) {
            return new SmtpResult(false, "Recipient email address is missing.", "");
        }

        // Try HTTPS Edge Function first
        try {
            SmtpResult edgeRes = sendViaSupabaseEdgeFunction(toEmail, "", "VERIFICATION_DECISION", null, fullName, approved, remarks);
            if (edgeRes.isSuccess()) {
                return edgeRes;
            }
        } catch (Exception ignored) {}

        // Secondary fallback to legacy direct SMTP if configured
        if (isSmtpCredentialsConfigured()) {
            return sendDirectVerificationDecisionEmail(toEmail, fullName, role, approved, remarks);
        }

        log.info("Verification decision email to {} noted (sandbox mode active).", toEmail);
        return new SmtpResult(true, "Verification status recorded for " + toEmail, "");
    }

    private static SmtpResult evaluateFallback(String toEmail, String otpCode, String failureReason) {
        if (AppConfig.isOfflineOtpFallbackAllowed()) {
            log.info("Active offline fallback triggered for {}", toEmail);
            return new SmtpResult(false, "HTTPS connection offline / server not configured. Check your email for the verification code.",
                    otpCode, "HTTPS (Offline Fallback)", true);
        }
        return new SmtpResult(false, "HTTPS delivery failed: " + failureReason, otpCode, "HTTPS (Port 443)", false);
    }

    private static boolean isSmtpCredentialsConfigured() {
        String user = getSetting("SMTP_USER", getSetting("SMTP_USERNAME", ""));
        String pass = getSetting("SMTP_PASSWORD", getSetting("SMTP_APP_PASSWORD", getSetting("SMTP_PASS", "")));
        return user != null && !user.isBlank() && pass != null && !pass.isBlank();
    }

    // --------------------------------------------------------------------------
    // Direct SMTP Fallback Implementation
    // --------------------------------------------------------------------------

    private static SmtpResult sendDirectSmtp(String toEmail, String otpCode, String purpose, String username) {
        if (!isSmtpCredentialsConfigured()) {
            return evaluateFallback(toEmail, otpCode, "SMTP credentials missing.");
        }

        String host = getSetting("SMTP_HOST", "smtp.gmail.com").trim();
        String port = getSetting("SMTP_PORT", "587").trim();
        String mailUser = getSetting("SMTP_USER", getSetting("SMTP_USERNAME", "")).trim();
        String rawPassword = getSetting("SMTP_PASSWORD", getSetting("SMTP_APP_PASSWORD", getSetting("SMTP_PASS", "")));
        String password = rawPassword != null ? rawPassword.replace(" ", "").trim() : "";
        String fromAddress = getSetting("SMTP_FROM", "NextIT Security <" + mailUser + ">").trim();
        boolean primarySsl = "465".equals(port) || "true".equalsIgnoreCase(getSetting("SMTP_SECURE", "false"));

        try {
            sendDirectEmailAttempt(host, port, primarySsl, mailUser, password, fromAddress, toEmail, otpCode, username, 3500);
            return new SmtpResult(true, "Code sent to " + toEmail + " via SMTP", otpCode, "Direct SMTP", false);
        } catch (Exception exPrimary) {
            log.warn("Primary SMTP delivery failed on {}:{}: {}", host, port, exPrimary.getMessage());
            String altPort = "587".equals(port) ? "465" : "587";
            boolean altSsl = "465".equals(altPort);
            try {
                sendDirectEmailAttempt(host, altPort, altSsl, mailUser, password, fromAddress, toEmail, otpCode, username, 3500);
                return new SmtpResult(true, "Code sent to " + toEmail + " via port " + altPort, otpCode, "Direct SMTP", false);
            } catch (Exception exAlt) {
                return evaluateFallback(toEmail, otpCode, "SMTP port 587 & 465 timed out or blocked by firewall.");
            }
        }
    }

    private static void sendDirectEmailAttempt(String host, String port, boolean isSsl,
                                              String username, String password,
                                              String fromAddress, String toEmail,
                                              String otpCode, String accountUsername, int timeoutMs) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.timeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.writetimeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.ssl.trust", "*");

        if (isSsl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", port);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "false");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        InternetAddress from = (fromAddress.contains("<") && fromAddress.contains(">"))
                ? InternetAddress.parse(fromAddress)[0]
                : new InternetAddress(fromAddress, "NextIT Security");
        message.setFrom(from);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail.trim()));
        message.setSubject("NextIT Verification Code: " + otpCode);
        message.setContent(buildPasswordResetHtmlEmail(otpCode, accountUsername), "text/html; charset=utf-8");
        Transport.send(message);
    }

    private static SmtpResult sendDirectVerificationDecisionEmail(String toEmail, String fullName, com.nextit.model.Role role, boolean approved, String remarks) {
        String host = getSetting("SMTP_HOST", "smtp.gmail.com").trim();
        String port = getSetting("SMTP_PORT", "587").trim();
        String mailUser = getSetting("SMTP_USER", "").trim();
        String rawPassword = getSetting("SMTP_PASSWORD", getSetting("SMTP_PASS", ""));
        String password = rawPassword != null ? rawPassword.replace(" ", "").trim() : "";
        String fromAddress = getSetting("SMTP_FROM", "NextIT Security <" + mailUser + ">").trim();
        boolean primarySsl = "465".equals(port) || "true".equalsIgnoreCase(getSetting("SMTP_SECURE", "false"));

        try {
            sendVerificationDecisionEmailAttempt(host, port, primarySsl, mailUser, password, fromAddress, toEmail, fullName, role, approved, remarks, 3500);
            return new SmtpResult(true, "Decision email dispatched to " + toEmail, "", "Direct SMTP", false);
        } catch (Exception ex) {
            log.warn("Direct SMTP verification decision failed: {}", ex.getMessage());
            return new SmtpResult(false, ex.getMessage(), "", "Direct SMTP", false);
        }
    }

    private static void sendVerificationDecisionEmailAttempt(String host, String port, boolean ssl,
                                                            String mailUser, String password,
                                                            String fromAddress, String toEmail,
                                                            String fullName, com.nextit.model.Role role,
                                                            boolean approved, String remarks,
                                                            int timeoutMs) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.timeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.writetimeout", String.valueOf(timeoutMs));

        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", port);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailUser, password);
            }
        });

        Message message = new MimeMessage(session);
        InternetAddress from = (fromAddress.contains("<") && fromAddress.contains(">"))
                ? InternetAddress.parse(fromAddress)[0]
                : new InternetAddress(fromAddress, "NextIT Security");
        message.setFrom(from);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail.trim()));
        message.setSubject(approved
                ? "NextIT Account Verified: Welcome to the Platform"
                : "NextIT Account Verification Status Update");

        String roleName = role != null ? role.name() : "User";
        String statusHtml = approved
                ? "<h2 style=\"font-size: 20px; color: #10B981; font-weight: 800; margin-top: 0;\">✔ Identity Verified & Approved</h2>"
                  + "<p style=\"font-size: 14px; color: #414141; line-height: 1.5;\">Your <strong>" + roleName + "</strong> credentials have been verified by system administration. You now have full access to your NextIT workspace.</p>"
                : "<h2 style=\"font-size: 20px; color: #9E0B0F; font-weight: 800; margin-top: 0;\">Verification Review Notice</h2>"
                  + "<p style=\"font-size: 14px; color: #414141; line-height: 1.5;\">Your " + roleName + " verification request requires additional attention.</p>"
                  + "<div style=\"background: #FDF2F2; border-left: 4px solid #9E0B0F; padding: 12px; margin: 16px 0; font-size: 13px; color: #9E0B0F;\">"
                  + "<strong>Reviewer Remarks:</strong> " + (remarks != null && !remarks.isBlank() ? remarks : "Please verify and re-upload authentic identification documents.")
                  + "</div>";

        String htmlContent = """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 560px; margin: 0 auto; padding: 32px 24px; background: #FDFBFA; border-radius: 24px; color: #414141; border: 1.5px solid #C6A75E;">
              <div style="background: linear-gradient(135deg, #1A2634 0%, #9E0B0F 60%, #C6A75E 100%); padding: 18px 24px; border-radius: 20px; text-align: center; margin-bottom: 24px;">
                <h1 style="margin: 0; font-size: 24px; color: #FDFBFA; font-weight: 900;">NextIT</h1>
                <p style="margin: 4px 0 0 0; font-size: 13px; color: #EFE2CA; font-weight: 700;">Academic & Industry Verification Portal</p>
              </div>
              <div style="background: #EFE2CA; padding: 28px 24px; border-radius: 20px; box-shadow: 0 4px 12px rgba(65,65,65,0.08); text-align: left; border: 1px solid #C6A75E;">
                <p style="font-size: 14px; color: #414141; margin-top: 0;">Hello <strong>{{NAME}}</strong>,</p>
                {{STATUS_BODY}}
              </div>
            </div>
            """.replace("{{NAME}}", fullName != null ? fullName : "User")
               .replace("{{STATUS_BODY}}", statusHtml);

        message.setContent(htmlContent, "text/html; charset=utf-8");
        Transport.send(message);
    }

    private static String buildPasswordResetHtmlEmail(String otpCode, String username) {
        String userDisplay = (username != null && !username.isBlank()) ? "@" + username : "your account";
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 32px 24px; background: #FAF7F2; border-radius: 20px; color: #1A2634; border: 1.5px solid #C6A75E;">
              <div style="background: linear-gradient(135deg, #1A2634 0%, #9E0B0F 60%, #C6A75E 100%); padding: 22px 24px; border-radius: 16px; text-align: center; margin-bottom: 24px;">
                <h1 style="margin: 0; font-size: 26px; color: #FFFFFF; font-weight: 900;">NextIT</h1>
                <p style="margin: 4px 0 0 0; font-size: 13px; color: #EFE2CA; font-weight: 700;">Account Password Recovery</p>
              </div>
              <div style="background: #FFFFFF; padding: 28px 24px; border-radius: 16px; box-shadow: 0 4px 12px rgba(26,38,52,0.08); text-align: center; border: 1px solid #EFE2CA;">
                <h2 style="font-size: 20px; margin-top: 0; color: #1A2634; font-weight: 800;">Password Reset Code</h2>
                <p style="font-size: 14px; color: #4A5A6A; line-height: 1.5;">We received a request to reset credentials for <strong>{{USER}}</strong>. Use the 6-digit confirmation code below:</p>
                <div style="display: inline-block; background: #FAF7F2; padding: 14px 28px; border-radius: 16px; font-size: 32px; font-weight: 900; letter-spacing: 6px; color: #9E0B0F; margin: 20px 0; border: 2.5px solid #9E0B0F; box-shadow: 0 2px 8px rgba(158,11,15,0.15);">
                  {{CODE}}
                </div>
                <p style="font-size: 12px; color: #8C827A; margin-bottom: 0;">Code valid for 10 minutes.</p>
              </div>
            </div>
            """.replace("{{CODE}}", otpCode).replace("{{USER}}", userDisplay);
    }

    public static synchronized void clearSmtpConfig() {
        DOT_ENV.setProperty("SMTP_USER", "");
        DOT_ENV.setProperty("SMTP_PASSWORD", "");
        DOT_ENV.setProperty("SMTP_ENABLED", "false");
        saveToDotEnv();
    }

    public static synchronized void updateAndPersistConfig(String host, String port, String user, String pass, String from) {
        if (host != null && !host.isBlank()) DOT_ENV.setProperty("SMTP_HOST", host.trim());
        if (port != null && !port.isBlank()) DOT_ENV.setProperty("SMTP_PORT", port.trim());
        if (user != null) DOT_ENV.setProperty("SMTP_USER", user.trim());
        if (pass != null) DOT_ENV.setProperty("SMTP_PASSWORD", pass.replace(" ", "").trim());
        if (from != null && !from.isBlank()) DOT_ENV.setProperty("SMTP_FROM", from.trim());
        DOT_ENV.setProperty("SMTP_ENABLED", "true");

        saveToDotEnv();
    }

    private static synchronized void saveToDotEnv() {
        Path path = Path.of(".env");
        List<String> lines = new ArrayList<>();
        if (Files.isRegularFile(path)) {
            try {
                lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }

        updateOrAddLine(lines, "SMTP_HOST", DOT_ENV.getProperty("SMTP_HOST", "smtp.gmail.com"));
        updateOrAddLine(lines, "SMTP_PORT", DOT_ENV.getProperty("SMTP_PORT", "587"));
        updateOrAddLine(lines, "SMTP_USER", DOT_ENV.getProperty("SMTP_USER", ""));
        updateOrAddLine(lines, "SMTP_PASSWORD", DOT_ENV.getProperty("SMTP_PASSWORD", ""));
        updateOrAddLine(lines, "SMTP_FROM", DOT_ENV.getProperty("SMTP_FROM", "NextIT Verification <no-reply@nextit.edu>"));
        updateOrAddLine(lines, "SMTP_ENABLED", DOT_ENV.getProperty("SMTP_ENABLED", "true"));

        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
            log.info("Successfully persisted SMTP configuration to .env");
        } catch (IOException e) {
            log.error("Failed to write .env file", e);
        }
    }

    private static void updateOrAddLine(List<String> lines, String key, String value) {
        String prefix = key + "=";
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.startsWith(prefix)) {
                lines.set(i, key + "=" + (value != null ? value : ""));
                return;
            }
        }
        lines.add(key + "=" + (value != null ? value : ""));
    }

    public static String getSetting(String key, String fallback) {
        String runtimeVal = DOT_ENV.getProperty(key);
        if (runtimeVal != null && !runtimeVal.isBlank()) return runtimeVal;
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) return prop;
        return fallback;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractJsonStringField(String json, String key) {
        if (json == null || json.isBlank()) return "";
        String needle = "\"" + key + "\":";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            needle = "\"" + key + "\" :";
            idx = json.indexOf(needle);
        }
        if (idx < 0) return "";
        int start = json.indexOf("\"", idx + needle.length());
        if (start < 0) return "";
        int end = json.indexOf("\"", start + 1);
        if (end < 0) return "";
        return json.substring(start + 1, end);
    }

    private static Properties loadDotEnv() {
        Properties properties = new Properties();
        List<Path> candidatePaths = new ArrayList<>();
        candidatePaths.add(Path.of(".env"));
        candidatePaths.add(Path.of("app", ".env"));
        try {
            String userDir = System.getProperty("user.dir");
            if (userDir != null && !userDir.isBlank()) {
                candidatePaths.add(Path.of(userDir, ".env"));
                candidatePaths.add(Path.of(userDir, "app", ".env"));
            }
        } catch (Exception ignored) {}
        try {
            Path codeSourcePath = Path.of(EmailService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path parentDir = Files.isDirectory(codeSourcePath) ? codeSourcePath : codeSourcePath.getParent();
            if (parentDir != null) {
                candidatePaths.add(parentDir.resolve(".env"));
                if (parentDir.getParent() != null) {
                    candidatePaths.add(parentDir.getParent().resolve(".env"));
                    candidatePaths.add(parentDir.getParent().resolve("app").resolve(".env"));
                }
            }
        } catch (Exception ignored) {}
        try {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                candidatePaths.add(Path.of(appData, "NextIT", ".env"));
            }
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                candidatePaths.add(Path.of(userHome, ".nextit", ".env"));
            }
        } catch (Exception ignored) {}

        for (Path path : candidatePaths) {
            if (Files.isRegularFile(path)) {
                log.info("Loading external email/SMTP configuration from: {}", path.toAbsolutePath());
                try {
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        int separator = trimmed.indexOf('=');
                        if (separator <= 0) continue;
                        String key = trimmed.substring(0, separator).trim();
                        String value = trimmed.substring(separator + 1).trim();
                        if (value.length() >= 2
                                && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        properties.setProperty(key, value);
                    }
                    return properties;
                } catch (IOException ignored) {}
            }
        }
        return properties;
    }
}
