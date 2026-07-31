package com.chhavi.security;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

import com.chhavi.dao.UserDao;
import com.chhavi.pojo.User;

@Component
public class AuthenticationFailureListener {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserDao userDao;

    public AuthenticationFailureListener(UserDao userDao) {
        this.userDao = userDao;
    }

    @EventListener
    public void authenticationFailed(
            AuthenticationFailureBadCredentialsEvent event) {

        String email = event.getAuthentication()
                .getName()
                .trim()
                .toLowerCase();

        User user = userDao.findByEmail(email).orElse(null);

        if (user == null) {
            return;
        }

        if (user.isAccountLocked()) {
            return;
        }

        int failedAttempts = user.getFailedLoginAttempts() + 1;

        user.setFailedLoginAttempts(failedAttempts);
        user.setUpdatedAt(LocalDateTime.now());

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {

            user.setAccountLocked(true);
            user.setLockTime(LocalDateTime.now());
        }

        userDao.saveUser(user);
    }
}