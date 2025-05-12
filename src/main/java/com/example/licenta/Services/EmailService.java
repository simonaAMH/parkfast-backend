package com.example.licenta.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.name}")
    private String appName;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendVerificationEmail(String to, String token) {
        String subject = "Verify Your Email Address";
        String verificationLink = frontendUrl + "/verify-email?token=" + token;

        String htmlContent =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                        "<div style='text-align: center; margin-bottom: 20px;'>" +
                        "<h1 style='color: #4a6cf7;'>" + appName + "</h1>" +
                        "</div>" +
                        "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-bottom: 20px;'>" +
                        "<h2 style='margin-top: 0; color: #333;'>Verify Your Email Address</h2>" +
                        "<p style='color: #555; line-height: 1.5;'>Thank you for registering with " + appName + ". Please click the button below to verify your email address:</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + verificationLink + "' style='background-color: #4a6cf7; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold;'>Verify Email</a>" +
                        "</div>" +
                        "<p style='color: #555; line-height: 1.5;'>If the button doesn't work, you can copy and paste the following link into your browser:</p>" +
                        "<p style='background-color: #e0e0e0; padding: 10px; border-radius: 3px; word-break: break-all;'>" + verificationLink + "</p>" +
                        "</div>" +
                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 20px;'>" +
                        "<p>This email was sent to you because someone registered for an account with this email address.</p>" +
                        "<p>If you didn't request this email, no action is needed.</p>" +
                        "</div>" +
                        "</div>";

        return sendHtmlEmail(to, subject, htmlContent);
    }

    public boolean sendPasswordResetEmail(String to, String token) {
        String subject = "Reset Your Password";
        String resetLink = frontendUrl + "/reset-password?token=" + token;

        String htmlContent =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                        "<div style='text-align: center; margin-bottom: 20px;'>" +
                        "<h1 style='color: #4a6cf7;'>" + appName + "</h1>" +
                        "</div>" +
                        "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-bottom: 20px;'>" +
                        "<h2 style='margin-top: 0; color: #333;'>Password Reset Request</h2>" +
                        "<p style='color: #555; line-height: 1.5;'>We received a request to reset your password. Click the button below to create a new password:</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + resetLink + "' style='background-color: #4a6cf7; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold;'>Reset Password</a>" +
                        "</div>" +
                        "<p style='color: #555; line-height: 1.5;'>If the button doesn't work, you can copy and paste the following link into your browser:</p>" +
                        "<p style='background-color: #e0e0e0; padding: 10px; border-radius: 3px; word-break: break-all;'>" + resetLink + "</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Note:</strong> This link will expire in 1 hour for security reasons.</p>" +
                        "</div>" +
                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 20px;'>" +
                        "<p>If you didn't request a password reset, you can safely ignore this email. Someone might have entered your email address by mistake.</p>" +
                        "</div>" +
                        "</div>";

        return sendHtmlEmail(to, subject, htmlContent);
    }

    public boolean sendAccountDeletionEmail(String to, String token) {
        String subject = "Confirm Account Deletion";
        String deletionLink = frontendUrl + "/confirm-deletion?token=" + token;

        String htmlContent =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                        "<div style='text-align: center; margin-bottom: 20px;'>" +
                        "<h1 style='color: #4a6cf7;'>" + appName + "</h1>" +
                        "</div>" +
                        "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-bottom: 20px;'>" +
                        "<h2 style='margin-top: 0; color: #333;'>Account Deletion Request</h2>" +
                        "<p style='color: #555; line-height: 1.5;'>We received a request to delete your account. This action will permanently remove all your data from our system.</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + deletionLink + "' style='background-color: #ff4d4f; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold;'>Confirm Deletion</a>" +
                        "</div>" +
                        "<p style='color: #555; line-height: 1.5;'>If the button doesn't work, you can copy and paste the following link into your browser:</p>" +
                        "<p style='background-color: #e0e0e0; padding: 10px; border-radius: 3px; word-break: break-all;'>" + deletionLink + "</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Warning:</strong> This action cannot be undone. All your account data will be permanently deleted.</p>" +
                        "</div>" +
                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 20px;'>" +
                        "<p>If you didn't request account deletion, please secure your account immediately by changing your password and contact our support team.</p>" +
                        "</div>" +
                        "</div>";

        return sendHtmlEmail(to, subject, htmlContent);
    }

    private boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("{} email sent to: {}", subject, to);
            return true;
        } catch (MailException | MessagingException e) {
            logger.error("Failed to send {} email to: {}. Error: {}", subject, to, e.getMessage());
            return false;
        }
    }
}