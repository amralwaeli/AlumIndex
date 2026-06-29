package com.alumindex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class MailService {

    // Resend transactional email API — sent over HTTPS so it works on hosts that
    // block outbound SMTP ports (e.g. Render's free tier).
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${alumindex.mail.resend-api-key:}")
    private String resendApiKey;

    @Value("${alumindex.mail.from:}")
    private String fromAddress;

    @Value("${alumindex.mail.from-name:AlumIndex}")
    private String fromName;

    @Value("${alumindex.frontend.origin:http://localhost:5173}")
    private String frontendOrigin;

    // ── Synchronous — exceptions propagate to caller (used in @Transactional flows) ──

    /**
     * Sends a university registration invite. NOT @Async so a delivery failure rolls
     * back the invite_token transaction and returns 500 to the operator.
     */
    public void sendInvite(String toEmail, String organization, String token) {
        String link = frontendOrigin + "/register/" + token;
        send(toEmail,
                "You've been invited to AlumIndex",
                "Hello,\n\n"
                + "You have been invited to register " + organization + " on AlumIndex.\n\n"
                + "Please complete your registration within 20 minutes:\n"
                + link + "\n\n"
                + "If you did not request this, ignore this email.\n\n"
                + "— AlumIndex Operator");
    }

    /**
     * Sends a team-member activation email. NOT @Async so a delivery failure rolls
     * back the user + activation_token transaction and returns 500 to the admin.
     */
    public void sendActivationInvite(String toEmail, String institutionName, String activationToken) {
        String link = frontendOrigin + "/activate/" + activationToken;
        send(toEmail,
                "Set your AlumIndex password",
                "Hello,\n\n"
                + "You have been added to " + institutionName + " on AlumIndex.\n\n"
                + "Please set your password by clicking the link below (valid for 24 hours):\n"
                + link + "\n\n"
                + "If you did not expect this invitation, you can safely ignore this email.\n\n"
                + "— AlumIndex Operator");
    }

    // ── Async (best-effort notifications — failures logged but not propagated) ──

    @Async
    public void sendDenial(String toEmail, String institutionName) {
        send(toEmail,
                "AlumIndex registration update",
                "Hello,\n\n"
                + "Unfortunately your registration request for " + institutionName
                + " was not approved at this time.\n\n"
                + "Please contact the operator for more information.\n\n"
                + "— AlumIndex Operator");
    }

    @Async
    public void sendSuspension(String toEmail, String institutionName) {
        send(toEmail,
                "Your AlumIndex subscription has been suspended",
                "Hello,\n\n"
                + "Your AlumIndex subscription for " + institutionName
                + " has been suspended.\n\n"
                + "Please contact the operator to discuss next steps.\n\n"
                + "— AlumIndex Operator");
    }

    @Async
    public void sendRenewal(String toEmail, String institutionName, Instant newEnd) {
        String endStr = newEnd != null ? newEnd.toString().substring(0, 10) : "N/A";
        send(toEmail,
                "Your AlumIndex agreement has been renewed",
                "Hello,\n\n"
                + "Your AlumIndex agreement for " + institutionName
                + " has been renewed.\n\n"
                + "New agreement end date: " + endStr + "\n\n"
                + "— AlumIndex Operator");
    }

    @Async
    public void sendReactivation(String toEmail, String institutionName) {
        send(toEmail,
                "Your AlumIndex access has been reactivated",
                "Hello,\n\n"
                + "Access to AlumIndex for " + institutionName
                + " has been reactivated. You may log in at any time.\n\n"
                + "Login: " + frontendOrigin + "/login\n\n"
                + "— AlumIndex Operator");
    }

    @Async
    public void sendOffboarding(String toEmail, String institutionName) {
        send(toEmail,
                "Your AlumIndex account has been closed",
                "Hello,\n\n"
                + "Your AlumIndex account for " + institutionName
                + " has been permanently closed and all associated data has been deleted.\n\n"
                + "If you believe this was done in error, please contact support.\n\n"
                + "— AlumIndex Operator");
    }

    // ── Core send — throws on any failure ────────────────────────────────────

    private void send(String to, String subject, String body) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException(
                "RESEND_API_KEY is not configured. Add alumindex.mail.resend-api-key to your profile.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException(
                "MAIL_FROM (sender address) is not configured. Add alumindex.mail.from to your profile.");
        }
        try {
            // Resend's "from" accepts a display name: "AlumIndex <no-reply@alumindex.org>".
            String from = (fromName == null || fromName.isBlank())
                    ? fromAddress
                    : fromName + " <" + fromAddress + ">";

            ObjectNode payload = json.createObjectNode();
            payload.put("from", from);
            payload.putArray("to").add(to);
            payload.put("subject", subject);
            payload.put("text", body);

            HttpRequest req = HttpRequest.newBuilder(URI.create(RESEND_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("authorization", "Bearer " + resendApiKey)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                    "Resend API returned HTTP " + resp.statusCode() + ": " + resp.body());
            }
            log.debug("Email sent to {}: {}", to, subject);
        } catch (IOException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new IllegalStateException("Failed to send email to " + to, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending email to {}", to);
            throw new IllegalStateException("Interrupted while sending email to " + to, e);
        }
    }
}
