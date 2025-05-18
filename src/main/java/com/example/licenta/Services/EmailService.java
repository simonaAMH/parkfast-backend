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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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

    private String formatDateTimeForEmail(OffsetDateTime dateTime) {
        if (dateTime == null) return "N/A";
        try {
            ZoneOffset gmtPlus3 = ZoneOffset.ofHours(3);
            OffsetDateTime dateTimeInGmtPlus3 = dateTime.withOffsetSameInstant(gmtPlus3);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return dateTimeInGmtPlus3.format(formatter);
        } catch (Exception e) {
            logger.warn("Could not format OffsetDateTime to 'yyyy-MM-dd HH:mm' in GMT+3 for email: " + dateTime + ". Error: " + e.getMessage(), e);
            return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    public boolean sendReservationConfirmationEmail(String toEmail, Long reservationId, String parkingLotName,
                                                    OffsetDateTime startTime, OffsetDateTime endTime,
                                                    BigDecimal amountPaid) {
        String subject = appName + ": Reservation Confirmed & Paid";
        String viewReservationLink = frontendUrl + "/reservation/" + reservationId;

        String htmlContent =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                        "<div style='text-align: center; margin-bottom: 20px;'>" +
                        "<h1 style='color: #4a6cf7;'>" + appName + "</h1>" +
                        "</div>" +
                        "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-bottom: 20px;'>" +
                        "<h2 style='margin-top: 0; color: #333;'>Reservation Confirmed!</h2>" +
                        "<p style='color: #555; line-height: 1.5;'>Thank you for your payment. Your parking reservation is confirmed.</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Reservation ID:</strong> " + reservationId + "</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Parking Lot:</strong> " + parkingLotName + "</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Start Time:</strong> " + formatDateTimeForEmail(startTime) + "</p>" +
                        (endTime != null ? "<p style='color: #555; line-height: 1.5;'><strong>End Time:</strong> " + formatDateTimeForEmail(endTime) + "</p>" : "") +
                        "<p style='color: #555; line-height: 1.5;'><strong>Amount Paid:</strong> " + (amountPaid != null ? amountPaid.setScale(2, RoundingMode.HALF_UP).toString() : "0.00") + " RON</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + viewReservationLink + "' style='background-color: #4a6cf7; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold;'>View Reservation</a>" +
                        "</div>" +
                        "<p style='color: #555; line-height: 1.5;'>You can view your reservation details by clicking the button above or by copying this link into your browser: " +
                        "<span style='background-color: #e0e0e0; padding: 5px; border-radius: 3px; word-break: break-all;'>" + viewReservationLink + "</span></p>" +
                        "</div>" +
                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 20px;'>" +
                        "<p>If you have any questions, please contact our support.</p>" +
                        "<p>&copy; " + java.time.Year.now().getValue() + " " + appName + ". All rights reserved.</p>" +
                        "</div>" +
                        "</div>";

        return sendHtmlEmail(toEmail, subject, htmlContent);
    }

    public boolean sendPayForUsageActiveEmail(String toEmail, Long reservationId, String parkingLotName, OffsetDateTime startTime) {
        String subject = appName + ": Your On-Demand Parking is Active!";
        String viewReservationLink = frontendUrl + "/reservation/" + reservationId;

        String htmlContent =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                        "<div style='text-align: center; margin-bottom: 20px;'>" +
                        "<h1 style='color: #4a6cf7;'>" + appName + "</h1>" +
                        "</div>" +
                        "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-bottom: 20px;'>" +
                        "<h2 style='margin-top: 0; color: #333;'>Your On-Demand Parking is Active!</h2>" +
                        "<p style='color: #555; line-height: 1.5;'>Your card has been successfully verified, and your pay-for-usage parking session is now active.</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Reservation ID:</strong> " + reservationId + "</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Parking Lot:</strong> " + parkingLotName + "</p>" +
                        "<p style='color: #555; line-height: 1.5;'><strong>Start Time:</strong> " + formatDateTimeForEmail(startTime) + "</p>" +
                        "<p style='color: #555; line-height: 1.5;'>You will be charged based on the duration of your stay when you end the reservation.</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + viewReservationLink + "' style='background-color: #4a6cf7; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold;'>View Reservation</a>" +
                        "</div>" +
                        "<p style='color: #555; line-height: 1.5;'>You can view or manage your active reservation by clicking the button above or by copying this link into your browser: " +
                        "<span style='background-color: #e0e0e0; padding: 5px; border-radius: 3px; word-break: break-all;'>" + viewReservationLink + "</span></p>" +
                        "</div>" +
                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 20px;'>" +
                        "<p>If you have any questions, please contact our support.</p>" +
                        "<p>&copy; " + java.time.Year.now().getValue() + " " + appName + ". All rights reserved.</p>" +
                        "</div>" +
                        "</div>";
        return sendHtmlEmail(toEmail, subject, htmlContent);
    }


    public boolean sendAccountCreationConfirmationEmail(String toEmail, String username) {
        String subject = "Welcome to " + appName + "!";
        String loginLink = frontendUrl + "/login";

        String htmlContent =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                        "<div style='text-align: center; margin-bottom: 20px;'>" +
                        "<h1 style='color: #4a6cf7;'>" + appName + "</h1>" +
                        "</div>" +
                        "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-bottom: 20px;'>" +
                        "<h2 style='margin-top: 0; color: #333;'>Welcome, " + username + "!</h2>" +
                        "<p style='color: #555; line-height: 1.5;'>Thank you for creating an account with " + appName + ". We're excited to have you on board.</p>" +
                        "<p style='color: #555; line-height: 1.5;'>Your account is now active. You may still need to verify your email address if you haven't already done so (please check for a separate verification email).</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + loginLink + "' style='background-color: #4CAF50; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold;'>Explore " + appName + "</a>" +
                        "</div>" +
                        "<p style='color: #555; line-height: 1.5;'>If you have any questions or need assistance, feel free to contact our support team.</p>" +
                        "</div>" +
                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 20px;'>" +
                        "<p>This is an automated message. Please do not reply directly to this email.</p>" +
                        "<p>&copy; " + java.time.Year.now().getValue() + " " + appName + ". All rights reserved.</p>" +
                        "</div>" +
                        "</div>";

        return sendHtmlEmail(toEmail, subject, htmlContent);
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
            logger.error("Failed to send {} email to: {}. Error: {}", subject, to, e.getMessage(), e); // Added exception to log
            return false;
        }
    }
}