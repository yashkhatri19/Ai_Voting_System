package com.chhavi.dao;

import java.util.Optional;
import com.chhavi.pojo.User;

public interface UserDao {

    User saveUser(User user);

    Optional<User> findByEmail(String email);

    Optional<User> findByVerificationOtp(String otp);

    boolean emailExists(String email);

    boolean mobileExists(String mobile);

    boolean voterIdExists(String voterId);
}