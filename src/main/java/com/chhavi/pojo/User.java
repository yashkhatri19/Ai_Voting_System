package com.chhavi.pojo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @NotBlank(message = "Full name is required")
    @Size(
        min = 3,
        max = 50,
        message = "Full name must be between 3 and 50 characters"
    )
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(
        min = 8,
        max = 100,
        message = "Password must contain at least 8 characters"
    )
    private String password;

    @NotBlank(message = "Mobile number is required")
    @Pattern(
        regexp = "^[6-9][0-9]{9}$",
        message = "Enter a valid 10 digit mobile number"
    )
    private String mobile;

    @NotBlank(message = "Voter ID is required")
    @Pattern(
        regexp = "^[A-Z]{3}[0-9]{7}$",
        message = "Voter ID must contain 3 uppercase letters followed by 7 digits"
    )
    private String voterId;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String role;

    private boolean hasVoted;

    private boolean emailVerified;

    private boolean accountLocked;

    private int failedLoginAttempts;

    private LocalDateTime lockTime;

    private String status;

    private String profileImage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String verificationOtp;

    private LocalDateTime verificationOtpExpiry;

    private LocalDateTime verificationOtpLastSentAt;

    public User() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getVoterId() {
        return voterId;
    }

    public void setVoterId(String voterId) {
        this.voterId = voterId;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isHasVoted() {
        return hasVoted;
    }

    public void setHasVoted(boolean hasVoted) {
        this.hasVoted = hasVoted;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getVerificationOtp() {
        return verificationOtp;
    }

    public void setVerificationOtp(String verificationOtp) {
        this.verificationOtp = verificationOtp;
    }

    public LocalDateTime getVerificationOtpExpiry() {
        return verificationOtpExpiry;
    }

    public void setVerificationOtpExpiry(LocalDateTime verificationOtpExpiry) {
        this.verificationOtpExpiry = verificationOtpExpiry;
    }

    public LocalDateTime getVerificationOtpLastSentAt() {
        return verificationOtpLastSentAt;
    }

    public void setVerificationOtpLastSentAt(LocalDateTime verificationOtpLastSentAt) {
        this.verificationOtpLastSentAt = verificationOtpLastSentAt;
    }

    @Override
    public String toString() {
        return "User [id=" + id
                + ", fullName=" + fullName
                + ", email=" + email
                + ", mobile=" + mobile
                + ", voterId=" + voterId
                + ", role=" + role
                + ", hasVoted=" + hasVoted
                + "]";
    }
}