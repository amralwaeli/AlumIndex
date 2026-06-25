package com.alumindex.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

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
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException(
                "MAIL_USERNAME is not configured. Add spring.mail.username to your local profile.");
        }
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.debug("Email sent to {}: {}", to, subject);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw e;
        }
    }
}
