// Supabase Edge Function: send-otp-email
// Dispatches 6-digit verification & recovery OTP emails over HTTPS (Port 443)
// Deploy command: supabase functions deploy send-otp-email --no-verify-jwt

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface SendOtpRequest {
  email: string;
  otpCode: string;
  purpose?: "ONBOARDING_VERIFICATION" | "PASSWORD_RESET" | "MFA" | "VERIFICATION_DECISION";
  username?: string;
  fullName?: string;
  decisionApproved?: boolean;
  decisionRemarks?: string;
}

serve(async (req: Request) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed. Use POST." }), {
        status: 405,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const payload: SendOtpRequest = await req.json();
    const { email, otpCode, purpose = "ONBOARDING_VERIFICATION", username, fullName, decisionApproved, decisionRemarks } = payload;

    if (!email || !email.includes("@")) {
      return new Response(JSON.stringify({ error: "Valid recipient email address is required." }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (!otpCode && purpose !== "VERIFICATION_DECISION") {
      return new Response(JSON.stringify({ error: "6-digit OTP code is required." }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Determine Subject and HTML Content based on purpose
    let subject = "Your NextIT Verification Code: " + otpCode;
    let htmlContent = "";

    if (purpose === "PASSWORD_RESET") {
      subject = "NextIT Password Recovery Code: " + otpCode;
      htmlContent = buildPasswordResetHtml(otpCode, username || email);
    } else if (purpose === "MFA") {
      subject = "NextIT Two-Factor Authentication Code: " + otpCode;
      htmlContent = buildMfaHtml(otpCode, username || email);
    } else if (purpose === "VERIFICATION_DECISION") {
      subject = decisionApproved
        ? "NextIT Account Approved: Verification Successful"
        : "NextIT Account Verification Status Update";
      htmlContent = buildDecisionHtml(fullName || username || email, decisionApproved ?? false, decisionRemarks);
    } else {
      // Default: ONBOARDING_VERIFICATION
      subject = "NextIT Account Verification Code: " + otpCode;
      htmlContent = buildOnboardingHtml(otpCode);
    }

    // Check transactional email provider secrets
    const resendApiKey = Deno.env.get("RESEND_API_KEY");
    const brevoApiKey = Deno.env.get("BREVO_API_KEY");
    const senderEmail = Deno.env.get("SENDER_EMAIL") || "NextIT Security <onboarding@resend.dev>";

    let provider = "sandbox";

    if (resendApiKey) {
      provider = "resend";
      const res = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${resendApiKey}`,
        },
        body: JSON.stringify({
          from: senderEmail,
          to: [email.trim()],
          subject: subject,
          html: htmlContent,
        }),
      });

      if (!res.ok) {
        const errText = await res.text();
        console.error("Resend delivery failed:", errText);
        throw new Error(`Resend error: ${res.status} ${errText}`);
      }
    } else if (brevoApiKey) {
      provider = "brevo";
      const res = await fetch("https://api.brevo.com/v3/smtp/email", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "api-key": brevoApiKey,
        },
        body: JSON.stringify({
          sender: { name: "NextIT Security", email: senderEmail },
          to: [{ email: email.trim() }],
          subject: subject,
          htmlContent: htmlContent,
        }),
      });

      if (!res.ok) {
        const errText = await res.text();
        console.error("Brevo delivery failed:", errText);
        throw new Error(`Brevo error: ${res.status} ${errText}`);
      }
    } else {
      // Development / Sandbox mode: Log code server-side
      console.log(`[NextIT Sandbox Email Dispatch via HTTPS] To: ${email} | Purpose: ${purpose} | Code: ${otpCode}`);
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: `OTP email dispatched successfully via HTTPS (${provider})`,
        provider: provider,
        email: email,
        purpose: purpose,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (error: any) {
    console.error("Supabase Edge Function error:", error);
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message || "Failed to dispatch email over HTTPS.",
      }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  }
});

// HTML Builders styled with NextIT Obsidian Navy / Crimson Red / Warm Gold Palette

function buildOnboardingHtml(otpCode: string): string {
  return `
    <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 32px 24px; background: #FAF7F2; border-radius: 20px; color: #1A2634; border: 1.5px solid #C6A75E;">
      <div style="background: linear-gradient(135deg, #1A2634 0%, #9E0B0F 60%, #C6A75E 100%); padding: 22px 24px; border-radius: 16px; text-align: center; margin-bottom: 24px;">
        <h1 style="margin: 0; font-size: 26px; color: #FFFFFF; font-weight: 900; letter-spacing: 1px;">NextIT</h1>
        <p style="margin: 6px 0 0 0; font-size: 13px; color: #F2E7D3; font-weight: 600;">Track Your Progress, Build Your Future</p>
      </div>
      <div style="background: #FFFFFF; padding: 28px 24px; border-radius: 16px; box-shadow: 0 4px 16px rgba(26,38,52,0.06); text-align: center; border: 1px solid #EFE2CA;">
        <h2 style="font-size: 20px; margin-top: 0; color: #1A2634; font-weight: 800;">Verify Your Identity</h2>
        <p style="font-size: 14px; color: #4A5A6A; line-height: 1.6;">Use the following 6-digit confirmation code to complete your NextIT onboarding verification:</p>
        <div style="display: inline-block; background: #FAF7F2; padding: 14px 32px; border-radius: 14px; font-size: 34px; font-weight: 900; letter-spacing: 8px; color: #9E0B0F; margin: 20px 0; border: 2.5px solid #9E0B0F; box-shadow: 0 2px 8px rgba(158,11,15,0.12);">
          ${otpCode}
        </div>
        <p style="font-size: 12px; color: #8C827A; margin-bottom: 0;">This code is valid for 10 minutes. If you did not request this verification, please disregard this email.</p>
      </div>
      <p style="font-size: 11px; color: #8C827A; text-align: center; margin-top: 20px;">Encrypted HTTPS Portal • NextIT Academic & Career Hub</p>
    </div>
  `;
}

function buildPasswordResetHtml(otpCode: string, accountName: string): string {
  return `
    <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 32px 24px; background: #FAF7F2; border-radius: 20px; color: #1A2634; border: 1.5px solid #C6A75E;">
      <div style="background: linear-gradient(135deg, #1A2634 0%, #9E0B0F 60%, #C6A75E 100%); padding: 22px 24px; border-radius: 16px; text-align: center; margin-bottom: 24px;">
        <h1 style="margin: 0; font-size: 26px; color: #FFFFFF; font-weight: 900; letter-spacing: 1px;">NextIT</h1>
        <p style="margin: 6px 0 0 0; font-size: 13px; color: #F2E7D3; font-weight: 600;">Account Password Recovery</p>
      </div>
      <div style="background: #FFFFFF; padding: 28px 24px; border-radius: 16px; box-shadow: 0 4px 16px rgba(26,38,52,0.06); text-align: center; border: 1px solid #EFE2CA;">
        <h2 style="font-size: 20px; margin-top: 0; color: #1A2634; font-weight: 800;">Password Recovery Code</h2>
        <p style="font-size: 14px; color: #4A5A6A; line-height: 1.6;">We received a password reset request for account <strong>@${accountName}</strong>. Enter the 6-digit confirmation code below to reset your password:</p>
        <div style="display: inline-block; background: #FAF7F2; padding: 14px 32px; border-radius: 14px; font-size: 34px; font-weight: 900; letter-spacing: 8px; color: #9E0B0F; margin: 20px 0; border: 2.5px solid #9E0B0F; box-shadow: 0 2px 8px rgba(158,11,15,0.12);">
          ${otpCode}
        </div>
        <p style="font-size: 12px; color: #8C827A; margin-bottom: 0;">This code will expire in 10 minutes. If you did not make this request, your account is secure and you can ignore this message.</p>
      </div>
      <p style="font-size: 11px; color: #8C827A; text-align: center; margin-top: 20px;">Encrypted HTTPS Portal • NextIT Academic Defense Network</p>
    </div>
  `;
}

function buildMfaHtml(otpCode: string, accountName: string): string {
  return `
    <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 32px 24px; background: #FAF7F2; border-radius: 20px; color: #1A2634; border: 1.5px solid #C6A75E;">
      <div style="background: linear-gradient(135deg, #1A2634 0%, #9E0B0F 60%, #C6A75E 100%); padding: 22px 24px; border-radius: 16px; text-align: center; margin-bottom: 24px;">
        <h1 style="margin: 0; font-size: 26px; color: #FFFFFF; font-weight: 900;">NextIT</h1>
        <p style="margin: 6px 0 0 0; font-size: 13px; color: #F2E7D3;">Two-Factor Authentication</p>
      </div>
      <div style="background: #FFFFFF; padding: 28px 24px; border-radius: 16px; text-align: center; border: 1px solid #EFE2CA;">
        <h2 style="font-size: 20px; margin-top: 0; color: #1A2634;">Login Verification Code</h2>
        <p style="font-size: 14px; color: #4A5A6A;">Enter this code to verify your sign-in attempt for <strong>@${accountName}</strong>:</p>
        <div style="display: inline-block; background: #FAF7F2; padding: 14px 32px; border-radius: 14px; font-size: 34px; font-weight: 900; letter-spacing: 8px; color: #9E0B0F; margin: 20px 0; border: 2.5px solid #9E0B0F;">
          ${otpCode}
        </div>
        <p style="font-size: 12px; color: #8C827A;">Code valid for 10 minutes.</p>
      </div>
    </div>
  `;
}

function buildDecisionHtml(name: string, approved: boolean, remarks?: string): string {
  const statusColor = approved ? "#10B981" : "#DC2626";
  const statusTitle = approved ? "✔ Account Verified & Approved" : "✖ Verification Update";
  const statusMsg = approved
    ? "Your institutional/industry verification request has been reviewed and approved by system administrators. You now have full access to your NextIT workspace."
    : "Your verification request could not be approved at this time.";

  return `
    <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 32px 24px; background: #FAF7F2; border-radius: 20px; color: #1A2634; border: 1.5px solid #C6A75E;">
      <div style="background: linear-gradient(135deg, #1A2634 0%, #9E0B0F 60%, #C6A75E 100%); padding: 22px 24px; border-radius: 16px; text-align: center; margin-bottom: 24px;">
        <h1 style="margin: 0; font-size: 26px; color: #FFFFFF; font-weight: 900;">NextIT</h1>
        <p style="margin: 6px 0 0 0; font-size: 13px; color: #F2E7D3;">Verification Workspace</p>
      </div>
      <div style="background: #FFFFFF; padding: 28px 24px; border-radius: 16px; border: 1px solid #EFE2CA;">
        <h2 style="font-size: 20px; color: ${statusColor}; margin-top: 0;">${statusTitle}</h2>
        <p style="font-size: 14px; color: #4A5A6A;">Hello <strong>${name}</strong>,</p>
        <p style="font-size: 14px; color: #4A5A6A; line-height: 1.6;">${statusMsg}</p>
        ${remarks ? `<div style="background: #FAF7F2; border-left: 4px solid #C6A75E; padding: 12px 16px; margin: 18px 0; font-size: 13px; color: #1A2634;"><strong>Admin Remarks:</strong> ${remarks}</div>` : ""}
        <p style="font-size: 12px; color: #8C827A; margin-top: 20px;">If you have questions, please reach out to campus administration.</p>
      </div>
    </div>
  `;
}
