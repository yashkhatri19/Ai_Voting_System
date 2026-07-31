package com.chhavi.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.chhavi.pojo.User;
import com.chhavi.pojo.Election;
import com.chhavi.pojo.Candidate;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.dev-fallback:false}")
    private boolean devFallback;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationOtpEmail(User user) {
        String otp = user.getVerificationOtp();
        String subject = "Verify Your Email - VOTE INDIA";
        String message = "VOTE INDIA\n"
                + "Digital Voting Portal\n\n"
                + "Verify your email address\n\n"
                + "Use the verification code below to complete your registration.\n\n"
                + "Verification Code: " + otp + "\n\n"
                + "This code expires in 10 minutes.\n"
                + "Do not share this code with anyone.\n\n"
                + "Best regards,\nVOTE INDIA Team";

        sendEmail(user.getEmail(), subject, message);
    }

    public void sendOtpEmail(String email, String otp) {
        String subject = "Password Reset OTP - AI Online Voting System";
        String message = "Your OTP for password reset is: " + otp + "\n\n"
                + "This OTP is valid for 10 minutes.\n\n"
                + "If you did not request a password reset, please ignore this email.";

        sendEmail(email, subject, message);
    }

    public void sendElectionNotificationEmail(User user, Election election) {
        String subject = "New Election Activated - AI Online Voting System";
        String message = "Dear " + user.getFullName() + ",\n\n"
                + "A new election has been activated: " + election.getTitle() + "\n\n"
                + "Description: " + election.getDescription() + "\n"
                + "Start Date: " + election.getStartDate() + "\n"
                + "End Date: " + election.getEndDate() + "\n\n"
                + "Please log in to your account and cast your vote.\n\n"
                + "Best regards,\nOnline Voting System Team";

        sendEmail(user.getEmail(), subject, message);
    }

    public void sendVoteConfirmationEmail(User user, Election election, Candidate candidate) {
        String subject = "Vote Recorded Successfully - AI Online Voting System";
        String message = "Dear " + user.getFullName() + ",\n\n"
                + "Your vote for the election '" + election.getTitle() + "' has been successfully recorded.\n"
                + "Candidate: " + candidate.getName() + " (" + candidate.getParty() + ")\n"
                + "Vote Time: " + java.time.LocalDateTime.now() + "\n\n"
                + "Thank you for participating in the democratic process!\n\n"
                + "Best regards,\nOnline Voting System Team";

        sendEmail(user.getEmail(), subject, message);
    }

    public void sendElectionResultEmail(User user, Election election, List<Map<String, Object>> candidateResults) {
        String subject = "Election Results Published - AI Online Voting System";
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(user.getFullName()).append(",\n\n");
        sb.append("The results for the election '").append(election.getTitle()).append("' have been published.\n\n");
        sb.append("Scoreboard:\n");
        sb.append("--------------------------------------------------\n");
        for (Map<String, Object> res : candidateResults) {
            sb.append(res.get("name")).append(" (").append(res.get("party")).append("): ")
              .append(res.get("votes")).append(" votes (")
              .append(String.format("%.2f", (Double) res.get("percentage"))).append("%)\n");
        }
        sb.append("--------------------------------------------------\n\n");
        sb.append("Thank you for participating.\n\n");
        sb.append("Best regards,\nOnline Voting System Team");

        sendEmail(user.getEmail(), subject, sb.toString());
    }

    private void sendEmail(String to, String subject, String text) {
        if (mailUsername == null || mailUsername.trim().isEmpty()) {
            if (devFallback) {
                logger.info("====== DEV MAIL SENDER (No credentials configured) ======");
                logger.info("To: {}", to);
                logger.info("Subject: {}", subject);
                logger.info("Body:\n{}", text);
                logger.info("========================================================");
                return;
            } else {
                throw new IllegalStateException("SMTP username is not configured and dev-fallback is disabled.");
            }
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(mailUsername);
            mailMessage.setTo(to);
            mailMessage.setSubject(subject);
            mailMessage.setText(text);
            mailSender.send(mailMessage);
        } catch (Exception e) {
            logger.error("Failed to send email to {}. Error: {}", to, e.getMessage(), e);
            if (devFallback) {
                logger.info("====== DEV MAIL SENDER FALLBACK ======");
                logger.info("To: {}", to);
                logger.info("Subject: {}", subject);
                logger.info("Body:\n{}", text);
                logger.info("======================================");
            } else {
                throw new RuntimeException("Failed to send verification email, please try again: " + e.getMessage(), e);
            }
        }
    }
}