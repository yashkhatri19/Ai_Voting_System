package com.chhavi.security;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import com.chhavi.dao.UserDao;
import com.chhavi.pojo.User;

@Component
public class AuthenticationSuccessListener {

    private final UserDao userDao;

    public AuthenticationSuccessListener(UserDao userDao) {
        this.userDao = userDao;
    }

    @EventListener
    public void authenticationSuccess(AuthenticationSuccessEvent event) {

        String email = event.getAuthentication()
                .getName()
                .trim()
                .toLowerCase();

        User user = userDao.findByEmail(email).orElse(null);

        if (user == null) {
            return;
        }

        if (user.getFailedLoginAttempts() > 0
                || user.isAccountLocked()
                || user.getLockTime() != null) {

            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setLockTime(null);
            user.setUpdatedAt(LocalDateTime.now());

            userDao.saveUser(user);
        }
    }
}