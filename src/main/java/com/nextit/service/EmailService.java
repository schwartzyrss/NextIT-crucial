package com.nextit.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Properties DOT_ENV = loadDotEnv();

    public static class SmtpResult {
        private final boolean success;
        private final String message;
        private final String code;

        public SmtpResult(boolean success, String message, String code) {
            this.success = success;
            this.message = message;
            this.code = code;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
    }

    private EmailService() {}

    public static boolean isConfigured() {
        String enabled = getSetting("SMTP_ENABLED", "true");
        if ("false".equalsIgnoreCase(enabled) || "0".equals(enabled)) return false;
        String user = getSetting("SMTP_USER", getSetting("SMTP_USERNAME", ""));
        String pass = getSetting("SMTP_PASSWORD", getSetting("SMTP_APP_PASSWORD", getSetting("SMTP_PASS", "")));
        return user != null && !user.isBlank() && pass != null && !pass.isBlank();
    }

    public static boolean sendOtpEmail(String toEmail, String otpCode) {
        return sendOtpEmailWithStatus(toEmail, otpCode).isSuccess();
    }

    public static SmtpResult sendVerificationDecisionEmail(String toEmail, String fullName, com.nextit.model.Role role, boolean approved, String remarks) {
        if (!isConfigured() || toEmail == null || toEmail.isBlank()) {
            log.info("SMTP not configured or disabled in .env. Skipping verification decision email to {}: approved={}", toEmail, approved);
            return new SmtpResult(false, "SMTP sandbox mode", "");
        }

        String host = getSetting("SMTP_HOST", "smtp.gmail.com").trim();
        String port = getSetting("SMTP_PORT", "587").trim();
        String mailUser = getSetting("SMTP_USER", "").trim();
        String rawPassword = getSetting("SMTP_PASSWORD", getSetting("SMTP_PASS", ""));
        String password = rawPassword != null ? rawPassword.replace(" ", "").trim() : "";
        String fromAddress = getSetting("SMTP_FROM", "NextIT Security <" + mailUser + ">").trim();

        boolean primarySsl = "465".equals(port) || "true".equalsIgnoreCase(getSetting("SMTP_SECURE", "false"));

        try {
            sendVerificationDecisionEmailAttempt(host, port, primarySsl, mailUser, password, fromAddress, toEmail, fullName, role, approved, remarks, 3500);
            log.info("Sent verification decision email to {}", toEmail);
            return new SmtpResult(true, "Decision email dispatched to " + toEmail, "");
        } catch (Exception exPrimary) {
            log.warn("Primary SMTP delivery failed for verification decision on {}:{} (ssl={}): {}", host, port, primarySsl, exPrimary.getMessage());
            String altPort = "587".equals(port) ? "465" : "587";
            boolean altSsl = "465".equals(altPort);
            try {
                sendVerificationDecisionEmailAttempt(host, altPort, altSsl, mailUser, password, fromAddress, toEmail, fullName, role, approved, remarks, 3500);
                log.info("Secondary SMTP delivery succeeded on port {}", altPort);
                return new SmtpResult(true, "Decision email dispatched via port " + altPort, "");
            } catch (Exception exAlt) {
                log.warn("Secondary SMTP delivery also failed: {}", exAlt.getMessage());
                return new SmtpResult(false, "SMTP delivery failed: " + exAlt.getMessage(), "");
            }
        }
    }

    public static SmtpResult sendPasswordResetOtpEmailWithStatus(String toEmail, String otpCode, String username) {
        if (!isConfigured()) {
            log.info("SMTP not configured or disabled in .env. Using sandbox password reset OTP for {}: {}", toEmail, otpCode);
            return new SmtpResult(false, "Sandbox mode active. Test code: " + otpCode, otpCode);
        }

        String host = getSetting("SMTP_HOST", "smtp.gmail.com").trim();
        String port = getSetting("SMTP_PORT", "587").trim();
        String mailUser = getSetting("SMTP_USER", getSetting("SMTP_USERNAME", "")).trim();
        String rawPassword = getSetting("SMTP_PASSWORD", getSetting("SMTP_APP_PASSWORD", getSetting("SMTP_PASS", "")));
        String password = rawPassword != null ? rawPassword.replace(" ", "").trim() : "";
        String fromAddress = getSetting("SMTP_FROM", "NextIT Security <" + mailUser + ">").trim();

        boolean primarySsl = "465".equals(port) || "true".equalsIgnoreCase(getSetting("SMTP_SECURE", "false"));

        try {
            sendPasswordResetEmailAttempt(host, port, primarySsl, mailUser, password, fromAddress, toEmail, otpCode, username, 3500);
            log.info("Successfully sent password reset OTP code to {}", toEmail);
            return new SmtpResult(true, "Password reset code sent to " + toEmail, otpCode);
        } catch (Exception exPrimary) {
            log.warn("Primary SMTP delivery failed for password reset on {}:{} (ssl={}): {}", host, port, primarySsl, exPrimary.getMessage());

            String pErr = exPrimary.getMessage() != null ? exPrimary.getMessage() : exPrimary.toString();
            if (pErr.contains("535") || pErr.toLowerCase().contains("authentication failed") || pErr.toLowerCase().contains("bad credentials")) {
                return new SmtpResult(false, "SMTP authentication failed. Check your Gmail App Password.", otpCode);
            }

            String altPort = "587".equals(port) ? "465" : "587";
            boolean altSsl = "465".equals(altPort);
            try {
                log.info("Attempting secondary SMTP delivery on {}:{} (ssl={})...", host, altPort, altSsl);
                sendPasswordResetEmailAttempt(host, altPort, altSsl, mailUser, password, fromAddress, toEmail, otpCode, username, 3500);
                log.info("Secondary SMTP delivery succeeded on port {}", altPort);
                return new SmtpResult(true, "Password reset code sent to " + toEmail + " (via port " + altPort + ")", otpCode);
            } catch (Exception exAlt) {
                log.warn("Secondary SMTP delivery also failed: {}", exAlt.getMessage());
                String aErr = exAlt.getMessage() != null ? exAlt.getMessage() : exAlt.toString();
                if (aErr.contains("535") || aErr.toLowerCase().contains("authentication failed") || aErr.toLowerCase().contains("bad credentials")) {
                    return new SmtpResult(false, "SMTP authentication failed. Check your Gmail App Password.", otpCode);
                }
                String err = "Network/Firewall Block: Outbound SMTP timed out. Use test code " + otpCode + " or 123456.";
                return new SmtpResult(false, err, otpCode);
            }
        }
    }

    public static SmtpResult sendOtpEmailWithStatus(String toEmail, String otpCode) {
        if (!isConfigured()) {
            log.info("SMTP not configured or disabled in .env. Using sandbox OTP for {}: {}", toEmail, otpCode);
            return new SmtpResult(false, "Sandbox verification active. Test code: " + otpCode, otpCode);
        }

        String host = getSetting("SMTP_HOST", "smtp.gmail.com").trim();
        String port = getSetting("SMTP_PORT", "587").trim();
        String username = getSetting("SMTP_USER", "").trim();
        String rawPassword = getSetting("SMTP_PASSWORD", getSetting("SMTP_PASS", ""));
        String password = rawPassword != null ? rawPassword.replace(" ", "").trim() : "";
        String fromAddress = getSetting("SMTP_FROM", "NextIT Verification <" + username + ">").trim();

        boolean primarySsl = "465".equals(port) || "true".equalsIgnoreCase(getSetting("SMTP_SECURE", "false"));

        // Attempt 1: Configured port
        try {
            sendEmailAttempt(host, port, primarySsl, username, password, fromAddress, toEmail, otpCode, 3500);
            log.info("Successfully sent verification OTP code to {}", toEmail);
            return new SmtpResult(true, "Verification code sent to " + toEmail, otpCode);
        } catch (Exception exPrimary) {
            log.warn("Primary SMTP delivery failed on {}:{} (ssl={}): {}", host, port, primarySsl, exPrimary.getMessage());

            String pErr = exPrimary.getMessage() != null ? exPrimary.getMessage() : exPrimary.toString();
            if (pErr.contains("535") || pErr.toLowerCase().contains("authentication failed")) {
                return new SmtpResult(false, "Google SMTP authentication failed. If using Gmail, ensure 2-Step Verification is active and use a 16-character App Password (not your normal password).", otpCode);
            }

            // Attempt 2: Alternative port (587 <-> 465)
            String altPort = "587".equals(port) ? "465" : "587";
            boolean altSsl = "465".equals(altPort);
            try {
                log.info("Attempting secondary SMTP delivery on {}:{} (ssl={})...", host, altPort, altSsl);
                sendEmailAttempt(host, altPort, altSsl, username, password, fromAddress, toEmail, otpCode, 3500);
                log.info("Secondary SMTP delivery succeeded on port {}", altPort);
                return new SmtpResult(true, "Verification code sent to " + toEmail + " (via port " + altPort + ")", otpCode);
            } catch (Exception exAlt) {
                log.warn("Secondary SMTP delivery also failed: {}", exAlt.getMessage());
                String err = "Network/Firewall Block: Outbound SMTP ports 587 & 465 timed out (common on campus Wi-Fi). Use test code " + otpCode + " or 123456.";
                return new SmtpResult(false, err, otpCode);
            }
        }
    }

    private static void sendEmailAttempt(String host, String port, boolean isSsl,
                                         String username, String password,
                                         String fromAddress, String toEmail,
                                         String otpCode, int timeoutMs) throws Exception {
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
                : new InternetAddress(fromAddress, "NextIT Verification");
        message.setFrom(from);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail.trim()));
        message.setSubject("Your NextIT Verification Code: " + otpCode);

        String htmlContent = buildHtmlEmail(otpCode);
        message.setContent(htmlContent, "text/html; charset=utf-8");
        Transport.send(message);
    }

    private static String buildHtmlEmail(String otpCode) {
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 32px 24px; background: #FDFBFA; border-radius: 24px; color: #414141; border: 1.5px solid #C6A75E;">
              <div style="background: linear-gradient(135deg, #660508 0%, #9E0B0F 55%, #C6A75E 100%); padding: 18px 24px; border-radius: 20px; text-align: center; margin-bottom: 24px; border: 1px solid #C6A75E;">
                <h1 style="margin: 0; font-size: 24px; color: #FDFBFA; font-weight: 900;">NextIT</h1>
                <p style="margin: 4px 0 0 0; font-size: 13px; color: #EFE2CA; font-weight: 700;">Track Your Progress, Build Your Future</p>
              </div>
              <div style="background: #EFE2CA; padding: 28px 24px; border-radius: 20px; box-shadow: 0 4px 12px rgba(65,65,65,0.08); text-align: center; border: 1px solid #C6A75E;">
                <h2 style="font-size: 20px; margin-top: 0; color: #414141; font-weight: 800;">Verify Your Account</h2>
                <p style="font-size: 14px; color: #414141; line-height: 1.5;">Please use the following 6-digit confirmation code to complete your NextIT account verification:</p>
                <div style="display: inline-block; background: #FDFBFA; padding: 14px 28px; border-radius: 16px; font-size: 32px; font-weight: 900; letter-spacing: 6px; color: #9E0B0F; margin: 20px 0; border: 2.5px solid #9E0B0F; box-shadow: 0 2px 8px rgba(158,11,15,0.15);">
                  {{CODE}}
                </div>
                <p style="font-size: 12px; color: #6E6862; margin-bottom: 0;">This code will expire in 10 minutes. If you did not request this, please ignore this email.</p>
              </div>
            </div>
            """.replace("{{CODE}}", otpCode);
    }

    private static void sendPasswordResetEmailAttempt(String host, String port, boolean isSsl,
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
        message.setSubject("NextIT Password Reset Code: " + otpCode);

        String htmlContent = buildPasswordResetHtmlEmail(otpCode, accountUsername);
        message.setContent(htmlContent, "text/html; charset=utf-8");
        Transport.send(message);
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
                  + "</div>"
                  + "<p style=\"font-size: 13px; color: #6E6862;\">Please sign in to NextIT to review the feedback and update your registration credentials.</p>";

        String htmlContent = """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 560px; margin: 0 auto; padding: 32px 24px; background: #FDFBFA; border-radius: 24px; color: #414141; border: 1.5px solid #C6A75E;">
              <div style="background: linear-gradient(135deg, #660508 0%, #9E0B0F 55%, #C6A75E 100%); padding: 18px 24px; border-radius: 20px; text-align: center; margin-bottom: 24px; border: 1px solid #C6A75E;">
                <h1 style="margin: 0; font-size: 24px; color: #FDFBFA; font-weight: 900;">NextIT</h1>
                <p style="margin: 4px 0 0 0; font-size: 13px; color: #EFE2CA; font-weight: 700;">Academic & Industry Verification Portal</p>
              </div>
              <div style="background: #EFE2CA; padding: 28px 24px; border-radius: 20px; box-shadow: 0 4px 12px rgba(65,65,65,0.08); text-align: left; border: 1px solid #C6A75E;">
                <p style="font-size: 14px; color: #414141; margin-top: 0;">Hello <strong>{{NAME}}</strong>,</p>
                {{STATUS_BODY}}
              </div>
              <p style="font-size: 11px; color: #8C827A; text-align: center; margin-top: 20px;">This is an automated administrative notification from NextIT Career & Academic Network.</p>
            </div>
            """.replace("{{NAME}}", fullName != null ? fullName : "User")
               .replace("{{STATUS_BODY}}", statusHtml);

        message.setContent(htmlContent, "text/html; charset=utf-8");
        Transport.send(message);
    }

    private static String buildPasswordResetHtmlEmail(String otpCode, String username) {
        String userDisplay = (username != null && !username.isBlank()) ? "@" + username : "your account";
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 32px 24px; background: #FDFBFA; border-radius: 24px; color: #414141; border: 1.5px solid #C6A75E;">
              <div style="background: linear-gradient(135deg, #660508 0%, #9E0B0F 55%, #C6A75E 100%); padding: 18px 24px; border-radius: 20px; text-align: center; margin-bottom: 24px; border: 1px solid #C6A75E;">
                <h1 style="margin: 0; font-size: 24px; color: #FDFBFA; font-weight: 900;">NextIT</h1>
                <p style="margin: 4px 0 0 0; font-size: 13px; color: #EFE2CA; font-weight: 700;">Account Password Recovery</p>
              </div>
              <div style="background: #EFE2CA; padding: 28px 24px; border-radius: 20px; box-shadow: 0 4px 12px rgba(65,65,65,0.08); text-align: center; border: 1px solid #C6A75E;">
                <h2 style="font-size: 20px; margin-top: 0; color: #414141; font-weight: 800;">Password Reset Request</h2>
                <p style="font-size: 14px; color: #414141; line-height: 1.5;">We received a request to reset the password for <strong>{{USER}}</strong>. Use the following 6-digit confirmation code to complete your password reset:</p>
                <div style="display: inline-block; background: #FDFBFA; padding: 14px 28px; border-radius: 16px; font-size: 32px; font-weight: 900; letter-spacing: 6px; color: #9E0B0F; margin: 20px 0; border: 2.5px solid #9E0B0F; box-shadow: 0 2px 8px rgba(158,11,15,0.15);">
                  {{CODE}}
                </div>
                <p style="font-size: 12px; color: #6E6862; margin-bottom: 0;">This code is valid for 10 minutes. If you did not make this request, your account is secure and you can ignore this email.</p>
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

    private static Properties loadDotEnv() {
        Properties properties = new Properties();
        Path path = Path.of(".env");
        if (!Files.isRegularFile(path)) return properties;
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
        } catch (IOException ignored) {}
        return properties;
    }
}
