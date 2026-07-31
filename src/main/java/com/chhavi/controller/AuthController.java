package com.chhavi.controller;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.chhavi.dao.AuthDao;
import com.chhavi.dao.UserDao;
import com.chhavi.dto.ForgotPasswordDto;
import com.chhavi.dto.OtpVerificationDto;
import com.chhavi.dto.ResetPasswordDto;
import com.chhavi.pojo.User;
import com.chhavi.utils.EmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
public class AuthController {

    private final AuthDao authDao;
    private final UserDao userDao;
    private final EmailService emailService;

    public AuthController(AuthDao authDao, UserDao userDao, EmailService emailService) {
        this.authDao = authDao;
        this.userDao = userDao;
        this.emailService = emailService;
    }

    @GetMapping("/")
    public String showHomePage() {
        return "home";
    }

    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @Valid @ModelAttribute("user") User user,
            BindingResult result,
            @RequestParam("profileImageFile") org.springframework.web.multipart.MultipartFile profileImageFile,
            HttpServletRequest request,
            Model model) {

        if (result.hasErrors()) {
            return "register";
        }

        try {
            if (profileImageFile != null && !profileImageFile.isEmpty()) {
                try {
                    byte[] bytes = profileImageFile.getBytes();
                    String base64Image = "data:" + profileImageFile.getContentType() + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
                    user.setProfileImage(base64Image);
                } catch (Exception e) {
                    System.err.println("Failed to encode profile image during registration: " + e.getMessage());
                }
            } else {
                user.setProfileImage("https://api.dicebear.com/7.x/adventurer/svg?seed=" + user.getFullName().replaceAll("\\s+", ""));
            }

            User savedUser = authDao.registerUser(user);
            emailService.sendVerificationOtpEmail(savedUser);
            
            // Set email in session for OTP verification safety
            request.getSession().setAttribute("pendingVerificationEmail", savedUser.getEmail());
            
            return "redirect:/verify-email-otp";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        } catch (Exception e) {
            model.addAttribute("error", "Registration failed due to a database or system error: " + e.getMessage());
            return "register";
        }
    }

    @GetMapping("/verify-email-otp")
    public String showVerifyEmailOtpPage(HttpServletRequest request, Model model) {
        String email = (String) request.getSession().getAttribute("pendingVerificationEmail");
        if (email == null) {
            return "redirect:/register";
        }
        OtpVerificationDto dto = new OtpVerificationDto();
        dto.setEmail(email);
        model.addAttribute("otpVerificationDto", dto);
        return "verify-email-otp";
    }

    @PostMapping("/verify-email-otp")
    public String verifyEmailOtp(
            @Valid @ModelAttribute("otpVerificationDto") OtpVerificationDto dto,
            BindingResult result,
            HttpServletRequest request,
            Model model) {

        if (result.hasErrors()) {
            return "verify-email-otp";
        }

        boolean verified = authDao.verifyEmailOtp(dto.getEmail(), dto.getOtp());
        if (verified) {
            request.getSession().removeAttribute("pendingVerificationEmail");
            return "redirect:/login?verified=true";
        } else {
            model.addAttribute("error", "Invalid or expired OTP. Please try again.");
            return "verify-email-otp";
        }
    }

    @PostMapping("/resend-verification-otp")
    public String resendVerificationOtp(
            @RequestParam String email,
            HttpServletRequest request,
            Model model) {

        User user = userDao.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            model.addAttribute("error", "User not found.");
            return "redirect:/register";
        }

        // Resend cooldown checks (60 seconds)
        if (user.getVerificationOtpLastSentAt() != null && 
            user.getVerificationOtpLastSentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
            model.addAttribute("error", "Please wait 60 seconds before requesting another OTP.");
            OtpVerificationDto dto = new OtpVerificationDto();
            dto.setEmail(email);
            model.addAttribute("otpVerificationDto", dto);
            return "verify-email-otp";
        }

        // Generate a new OTP and expiry
        SecureRandom random = new SecureRandom();
        int otpNum = 100000 + random.nextInt(900000);
        user.setVerificationOtp(String.valueOf(otpNum));
        user.setVerificationOtpExpiry(LocalDateTime.now().plusMinutes(10));
        user.setVerificationOtpLastSentAt(LocalDateTime.now());
        userDao.saveUser(user);

        try {
            emailService.sendVerificationOtpEmail(user);
            model.addAttribute("success", "A new verification code has been sent to your email.");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to send verification email, please try again: " + e.getMessage());
        }
        
        OtpVerificationDto dto = new OtpVerificationDto();
        dto.setEmail(email);
        model.addAttribute("otpVerificationDto", dto);
        return "verify-email-otp";
    }

    @GetMapping("/login")
    public String showLoginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "verified", required = false) String verified,
            HttpServletRequest request,
            Model model) {

        if (error != null) {
            HttpSession session = request.getSession(false);
            String errorMessage = "Invalid email or password.";
            if (session != null) {
                Object lastException = session.getAttribute("SPRING_SECURITY_LAST_EXCEPTION");
                if (lastException instanceof org.springframework.security.core.AuthenticationException) {
                    String exceptionMessage = ((org.springframework.security.core.AuthenticationException) lastException).getMessage();
                    if ("Bad credentials".equalsIgnoreCase(exceptionMessage)) {
                        errorMessage = "Invalid email or password.";
                    } else if (exceptionMessage != null && (exceptionMessage.contains("timeout") || exceptionMessage.contains("Mongo") || exceptionMessage.contains("SSL") || exceptionMessage.contains("Socket"))) {
                        errorMessage = "Unable to connect to the service. Please try again later.";
                    } else {
                        errorMessage = exceptionMessage;
                    }
                }
            }
            model.addAttribute("error", errorMessage);
        }
        if (logout != null) {
            model.addAttribute("success", "You have been logged out successfully.");
        }
        if (verified != null) {
            model.addAttribute("success", "Email verified successfully. You can now login.");
        }
        return "login";
    }

    @GetMapping("/login-success")
    public String loginSuccess(org.springframework.security.core.Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/voter/dashboard";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage(Model model) {
        model.addAttribute("forgotPasswordDto", new ForgotPasswordDto());
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @Valid @ModelAttribute("forgotPasswordDto") ForgotPasswordDto dto,
            BindingResult result,
            Model model) {

        if (result.hasErrors()) {
            return "forgot-password";
        }

        try {
            authDao.requestPasswordReset(dto.getEmail());
            model.addAttribute("success", "If an account exists for this email, an OTP has been sent.");
            return "redirect:/verify-otp?email=" + dto.getEmail();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "forgot-password";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to process request, please try again: " + e.getMessage());
            return "forgot-password";
        }
    }

    @GetMapping("/verify-otp")
    public String showVerifyOtpPage(@RequestParam String email, Model model) {
        OtpVerificationDto dto = new OtpVerificationDto();
        dto.setEmail(email);
        model.addAttribute("otpVerificationDto", dto);
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String processVerifyOtp(
            @Valid @ModelAttribute("otpVerificationDto") OtpVerificationDto dto,
            BindingResult result,
            Model model) {

        if (result.hasErrors()) {
            return "verify-otp";
        }

        boolean verified = authDao.verifyOtp(dto.getEmail(), dto.getOtp());
        if (verified) {
            return "redirect:/reset-password?email=" + dto.getEmail() + "&otp=" + dto.getOtp();
        } else {
            model.addAttribute("error", "Invalid or expired OTP. Please try again.");
            return "verify-otp";
        }
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(
            @RequestParam String email,
            @RequestParam String otp,
            Model model) {

        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setEmail(email);
        dto.setOtp(otp);
        model.addAttribute("resetPasswordDto", dto);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @Valid @ModelAttribute("resetPasswordDto") ResetPasswordDto dto,
            BindingResult result,
            Model model) {

        if (result.hasErrors()) {
            return "reset-password";
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            model.addAttribute("error", "Passwords do not match.");
            return "reset-password";
        }

        try {
            boolean success = authDao.resetPassword(dto.getEmail(), dto.getOtp(), dto.getPassword());
            if (success) {
                model.addAttribute("success", "Password reset successful. Please login with your new password.");
                return "login";
            } else {
                model.addAttribute("error", "Invalid or expired OTP. Please request a new one.");
                return "forgot-password";
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "reset-password";
        }
    }
}