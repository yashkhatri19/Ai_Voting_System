package com.chhavi.security;

import java.time.LocalDateTime;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.chhavi.dao.UserDao;
import com.chhavi.pojo.User;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserDao userDao;

    public CustomUserDetailsService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userDao.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.isAccountLocked()) {
            if (user.getLockTime() != null && user.getLockTime().plusMinutes(30).isBefore(LocalDateTime.now())) {
                user.setAccountLocked(false);
                user.setFailedLoginAttempts(0);
                user.setLockTime(null);
                userDao.saveUser(user);
            } else {
                throw new LockedException("Your account is temporarily locked due to multiple failed login attempts. Please try again after 30 minutes.");
            }
        }

        if (!user.isEmailVerified()) {
            throw new DisabledException("Please verify your email before logging in.");
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().toUpperCase())
                .build();
    }
}