package com.chhavi.daoimpl;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import com.chhavi.dao.AuthDao;
import com.chhavi.dao.UserDao;
import com.chhavi.pojo.PasswordResetOtp;
import com.chhavi.pojo.User;
import com.chhavi.repository.PasswordResetOtpRepository;
import com.chhavi.utils.EmailService;

@Repository
public class AuthDaoImpl implements AuthDao {

    private static final Logger logger = LoggerFactory.getLogger(AuthDaoImpl.class);

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetOtpRepository otpRepository;
    private final EmailService emailService;

    public AuthDaoImpl(UserDao userDao, PasswordEncoder passwordEncoder, 
                       PasswordResetOtpRepository otpRepository, EmailService emailService) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
    }

    @Override
    public User registerUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        // 1. Validation
        if (user.getFullName() == null || user.getFullName().trim().length() < 3 || user.getFullName().trim().length() > 50) {
            throw new IllegalArgumentException("Full name must be between 3 and 50 characters");
        }
        if (user.getEmail() == null || !Pattern.matches("^[A-Za-z0-9+_.-]+@(.+)$", user.getEmail())) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        if (user.getPassword() == null || user.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        String passRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^.()_=+-\\[\\]{}|;:',<>/?`~]).+$";
        if (!Pattern.matches(passRegex, user.getPassword())) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character");
        }
        if (user.getMobile() == null || !Pattern.matches("^[6-9][0-9]{9}$", user.getMobile())) {
            throw new IllegalArgumentException("Enter a valid 10 digit Indian mobile number starting with 6, 7, 8 or 9");
        }
        if (user.getVoterId() == null || !Pattern.matches("^[A-Z]{3}[0-9]{7}$", user.getVoterId().toUpperCase())) {
            throw new IllegalArgumentException("Voter ID must be 3 uppercase letters followed by 7 digits");
        }
        if (user.getDateOfBirth() == null || user.getDateOfBirth().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth must be a past date");
        }

        int age = Period.between(user.getDateOfBirth(), LocalDate.now()).getYears();
        if (age < 18) {
            throw new IllegalArgumentException("You must be at least 18 years old to register");
        }

        // 2. Uniqueness checks
        String normalizedEmail = user.getEmail().trim().toLowerCase();
        if (userDao.emailExists(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered");
        }

        String normalizedMobile = user.getMobile().trim();
        if (userDao.mobileExists(normalizedMobile)) {
            throw new IllegalArgumentException("Mobile number already registered");
        }

        String normalizedVoterId = user.getVoterId().trim().toUpperCase();
        if (userDao.voterIdExists(normalizedVoterId)) {
            throw new IllegalArgumentException("Voter ID already registered");
        }

        // 3. Normalization and setup
        user.setEmail(normalizedEmail);
        user.setFullName(user.getFullName().trim());
        user.setMobile(normalizedMobile);
        user.setVoterId(normalizedVoterId);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        user.setRole("VOTER");
        user.setHasVoted(false);
        user.setEmailVerified(false);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockTime(null);
        
        // Generate secure 6 digit registration OTP
        SecureRandom random = new SecureRandom();
        int otpNum = 100000 + random.nextInt(900000);
        user.setVerificationOtp(String.valueOf(otpNum));
        user.setVerificationOtpExpiry(LocalDateTime.now().plusMinutes(10));
        user.setVerificationOtpLastSentAt(LocalDateTime.now());
        
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        try {
            return userDao.saveUser(user);
        } catch (Exception e) {
            logger.error("User registration database save failed for email {}: {}", user.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public boolean verifyEmailOtp(String email, String otp) {
        if (email == null || otp == null || otp.trim().isEmpty()) {
            return false;
        }

        User user = userDao.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return false;
        }

        if (user.getVerificationOtp() == null || !user.getVerificationOtp().equals(otp.trim())) {
            return false;
        }

        if (user.getVerificationOtpExpiry() != null && user.getVerificationOtpExpiry().isBefore(LocalDateTime.now())) {
            return false;
        }

        user.setEmailVerified(true);
        user.setVerificationOtp(null);
        user.setVerificationOtpExpiry(null);
        user.setUpdatedAt(LocalDateTime.now());

        userDao.saveUser(user);
        return true;
    }

    @Override
    public void requestPasswordReset(String email) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        User user = userDao.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return;
        }

        // Cooldown check (60 seconds)
        List<PasswordResetOtp> existingOtps = otpRepository.findByUser(user);
        for (PasswordResetOtp existing : existingOtps) {
            if (!existing.isUsed() && existing.getCreatedAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Please wait 60 seconds before requesting another OTP.");
            }
        }

        // Invalidate previous unused OTPs
        for (PasswordResetOtp existing : existingOtps) {
            if (!existing.isUsed()) {
                existing.setUsed(true);
                otpRepository.save(existing);
            }
        }

        // Generate 6 digit numeric OTP
        SecureRandom random = new SecureRandom();
        int otpNum = 100000 + random.nextInt(900000);
        String otp = String.valueOf(otpNum);

        // Hash the OTP
        String otpHash = hashOtp(otp);

        // Save new OTP record
        PasswordResetOtp otpEntity = new PasswordResetOtp();
        otpEntity.setUser(user);
        otpEntity.setOtpHash(otpHash);
        otpEntity.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        otpEntity.setUsed(false);
        otpEntity.setAttempts(0);
        otpEntity.setCreatedAt(LocalDateTime.now());
        otpRepository.save(otpEntity);

        // Send email
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Override
    public boolean verifyOtp(String email, String otp) {
        if (email == null || otp == null || otp.trim().isEmpty()) {
            return false;
        }

        User user = userDao.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return false;
        }

        PasswordResetOtp otpEntity = otpRepository.findByUserAndUsedFalse(user).orElse(null);
        if (otpEntity == null) {
            return false;
        }

        // Expiry check
        if (otpEntity.getExpiryTime().isBefore(LocalDateTime.now())) {
            otpEntity.setUsed(true);
            otpRepository.save(otpEntity);
            return false;
        }

        // Attempts check (limit 5)
        if (otpEntity.getAttempts() >= 5) {
            otpEntity.setUsed(true);
            otpRepository.save(otpEntity);
            return false;
        }

        otpEntity.setAttempts(otpEntity.getAttempts() + 1);
        otpRepository.save(otpEntity);

        String inputHash = hashOtp(otp);
        if (otpEntity.getOtpHash().equals(inputHash)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean resetPassword(String email, String otp, String newPassword) {
        if (email == null || otp == null || newPassword == null) {
            return false;
        }

        User user = userDao.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return false;
        }

        PasswordResetOtp otpEntity = otpRepository.findByUserAndUsedFalse(user).orElse(null);
        if (otpEntity == null) {
            return false;
        }

        if (otpEntity.getExpiryTime().isBefore(LocalDateTime.now()) || otpEntity.getAttempts() >= 5) {
            otpEntity.setUsed(true);
            otpRepository.save(otpEntity);
            return false;
        }

        String inputHash = hashOtp(otp);
        if (!otpEntity.getOtpHash().equals(inputHash)) {
            return false;
        }

        // Validate new password strictly
        String passRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^.()_=+-\\[\\]{}|;:',<>/?`~]).+$";
        if (newPassword.length() < 8 || !Pattern.matches(passRegex, newPassword)) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character");
        }

        // BCrypt encode
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userDao.saveUser(user);

        // Mark OTP as used
        otpEntity.setUsed(true);
        otpRepository.save(otpEntity);

        return true;
    }

    private String hashOtp(String otp) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(otp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing OTP", e);
        }
    }
}