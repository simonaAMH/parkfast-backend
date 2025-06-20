package com.example.licenta.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

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

    public boolean sendReservationConfirmationEmail(String toEmail, String reservationId, String parkingLotName,
                                                    OffsetDateTime startTime, OffsetDateTime endTime,
                                                    Double amountPaid, @Nullable String guestAccessToken) {
        String subject = appName + ": Reservation Confirmed & Paid";
        String baseLink = frontendUrl + "/reservation/" + reservationId;

        String viewReservationLink = baseLink;
        if (guestAccessToken != null && !guestAccessToken.isEmpty()) {
            viewReservationLink += "?access_token=" + guestAccessToken;
        }

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
                        "<p style='color: #555; line-height: 1.5;'><strong>Start Time:</strong> " + formatDateTimeForEmail(startTime) + " (GMT+03:00)</p>" +
                        (endTime != null ? "<p style='color: #555; line-height: 1.5;'><strong>End Time:</strong> " + formatDateTimeForEmail(endTime) + " (GMT+03:00)</p>" : "") +
                        "<p style='color: #555; line-height: 1.5;'><strong>Amount Paid:</strong> " + (amountPaid != null ? amountPaid : "0.00") + " RON</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + viewReservationLink + "' style='background-color: #4a6cf7; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold;'>View Reservation</a>" +
                        "</div>" +
                        "<p style='color: #555; line-height: 1.5;'>You can view your reservation details by clicking the button above or by copying this link into your browser: " +
                        "<span style='background-color: #e0e0e0; padding: 5px; border-radius: 3px; word-break: break-all;'>" + viewReservationLink + "</span></p>" +
                        (guestAccessToken != null && !guestAccessToken.isEmpty() ? "<p style='color: #777; font-size: 12px; line-height: 1.4;'><i>This special access link will expire approximately 1 hour after your reservation end time.</i></p>" : "") +
                        "</div>" +
                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 20px;'>" +
                        "<p>If you have any questions, please contact our support.</p>" +
                        "<p>&copy; " + java.time.Year.now().getValue() + " " + appName + ". All rights reserved.</p>" +
                        "</div>" +
                        "</div>";

        return sendHtmlEmail(toEmail, subject, htmlContent);
    }

    public boolean sendPayForUsageActiveEmail(String toEmail, String reservationId, String parkingLotName, OffsetDateTime startTime,
                                              @Nullable String guestAccessToken) {
        String subject = appName + ": Your On-Demand Parking is Active!";
        String baseLink = frontendUrl + "/reservation/" + reservationId;

        String viewReservationLink = baseLink;
        if (guestAccessToken != null && !guestAccessToken.isEmpty()) {
            viewReservationLink += "?access_token=" + guestAccessToken;
        }

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

    public boolean sendWithdrawalConfirmationEmail(String toEmail, String withdrawalId, Double withdrawnAmount,
                                                   String bankAccountNumber, String parkingLotName) {
        String subject = appName + ": Withdrawal Processed Successfully";

        // Format the current date and time for display
        String processedDate = formatDateTimeForEmail(OffsetDateTime.now());

        // Mask the bank account number for security (show only last 4 digits)
        String maskedBankAccount = "****" + bankAccountNumber.substring(Math.max(0, bankAccountNumber.length() - 4));

        String htmlContent =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>" +
                        "<div style='text-align: center; margin-bottom: 20px;'>" +
                        "<h1 style='color: #4a6cf7;'>" + appName + "</h1>" +
                        "</div>" +
                        "<div style='background-color: #f0f8ff; padding: 20px; border-radius: 5px; margin-bottom: 20px; border-left: 4px solid #22C55E;'>" +
                        "<h2 style='margin-top: 0; color: #22C55E;'>✅ Withdrawal Processed Successfully!</h2>" +
                        "<p style='color: #555; line-height: 1.6; font-size: 16px;'>Great news! Your withdrawal request has been processed and the funds are on their way to your bank account.</p>" +
                        "</div>" +

                        "<div style='background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-bottom: 20px;'>" +
                        "<h3 style='margin-top: 0; color: #333; border-bottom: 2px solid #4a6cf7; padding-bottom: 10px;'>Withdrawal Details</h3>" +
                        "<div style='display: flex; flex-direction: column; gap: 12px;'>" +
                        "<div style='display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee;'>" +
                        "<span style='color: #666; font-weight: 500;'>Withdrawal ID:</span>" +
                        "<span style='color: #333; font-family: monospace; background: #f0f0f0; padding: 2px 6px; border-radius: 3px;'>" + withdrawalId + "</span>" +
                        "</div>" +
                        "<div style='display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee;'>" +
                        "<span style='color: #666; font-weight: 500;'>Amount:</span>" +
                        "<span style='color: #22C55E; font-weight: bold; font-size: 18px;'>" + String.format("%.2f", withdrawnAmount) + " RON</span>" +
                        "</div>" +
                        "<div style='display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee;'>" +
                        "<span style='color: #666; font-weight: 500;'>Parking Lot:</span>" +
                        "<span style='color: #333; font-weight: 500;'>" + parkingLotName + "</span>" +
                        "</div>" +
                        "<div style='display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee;'>" +
                        "<span style='color: #666; font-weight: 500;'>Bank Account:</span>" +
                        "<span style='color: #333; font-family: monospace;'>" + maskedBankAccount + "</span>" +
                        "</div>" +
                        "<div style='display: flex; justify-content: space-between; padding: 8px 0;'>" +
                        "<span style='color: #666; font-weight: 500;'>Processed At:</span>" +
                        "<span style='color: #333;'>" + processedDate + " (GMT+03:00)</span>" +
                        "</div>" +
                        "</div>" +
                        "</div>" +

                        "<div style='background-color: #fff3cd; border: 1px solid #ffeaa7; border-radius: 5px; padding: 15px; margin-bottom: 20px;'>" +
                        "<h4 style='margin-top: 0; color: #856404;'>📋 What happens next?</h4>" +
                        "<ul style='color: #856404; margin: 0; padding-left: 20px; line-height: 1.6;'>" +
                        "<li>The funds have been transferred to your registered bank account</li>" +
                        "<li>Depending on your bank, it may take <strong>1-3 business days</strong> for the transfer to appear</li>" +
                        "<li>You will receive a notification from your bank once the funds arrive</li>" +
                        "<li>Your pending earnings balance has been updated in your " + appName + " account</li>" +
                        "</ul>" +
                        "</div>" +

                        "<div style='background-color: #e8f5e8; border: 1px solid #c3e6c3; border-radius: 5px; padding: 15px; margin-bottom: 20px;'>" +
                        "<h4 style='margin-top: 0; color: #2d5a2d;'>💡 Need Help?</h4>" +
                        "<p style='color: #2d5a2d; margin: 0; line-height: 1.6;'>" +
                        "If you don't see the funds in your account after 3 business days, or if you have any questions about this withdrawal, " +
                        "please contact our support team with your withdrawal ID: <strong>" + withdrawalId + "</strong>" +
                        "</p>" +
                        "</div>" +

                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "<a href='" + frontendUrl + "/dashboard' style='background-color: #4a6cf7; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold; display: inline-block;'>View Dashboard</a>" +
                        "</div>" +

                        "<div style='color: #999; font-size: 12px; text-align: center; margin-top: 30px; border-top: 1px solid #eee; padding-top: 20px;'>" +
                        "<p style='margin: 5px 0;'>This is an automated message confirming your withdrawal request.</p>" +
                        "<p style='margin: 5px 0;'>Please do not reply directly to this email.</p>" +
                        "<p style='margin: 5px 0;'>For support inquiries, please contact us through the app or our website.</p>" +
                        "<p style='margin: 15px 0 5px 0;'>&copy; " + java.time.Year.now().getValue() + " " + appName + ". All rights reserved.</p>" +
                        "</div>" +
                        "</div>";

        return sendHtmlEmail(toEmail, subject, htmlContent);
    }
}