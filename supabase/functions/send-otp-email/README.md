# Supabase Edge Function: `send-otp-email`

Dispatches 6-digit verification and recovery OTP emails for NextIT over standard **HTTPS (Port 443)**.

## Why HTTPS Dispatch?
Raw SMTP ports (`587` and `465`) are frequently blocked or throttled by campus Wi-Fi networks and ISP firewalls. This Edge Function accepts requests from NextIT desktop clients via standard HTTPS (Port 443), keeping server credentials (e.g., Resend, Brevo, or transactional SMTP) secure in Supabase secrets without exposing them in the client application binary.

## Deployment

1. Install the Supabase CLI if not already installed:
   ```bash
   npm install -g supabase
   ```

2. Link to your project (if not linked):
   ```bash
   supabase link --project-ref qlmrcmaqkvtmciursqvr
   ```

3. Deploy the Edge Function:
   ```bash
   supabase functions deploy send-otp-email --no-verify-jwt
   ```

4. Configure server-side secrets (optional for transactional delivery):
   ```bash
   # Option 1: Resend (Recommended)
   supabase secrets set RESEND_API_KEY=re_123456789 SENDER_EMAIL="NextIT <onboarding@resend.dev>"

   # Option 2: Brevo
   supabase secrets set BREVO_API_KEY=xkeysib-123456789 SENDER_EMAIL="NextIT <noreply@yourdomain.com>"
   ```
   *(If no API key is set, the function runs in sandbox mode, logging OTP dispatches in Supabase logs and returning a successful JSON response).*
