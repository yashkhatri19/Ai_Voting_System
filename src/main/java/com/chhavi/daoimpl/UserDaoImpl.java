package com.chhavi.daoimpl;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import com.chhavi.dao.UserDao;
import com.chhavi.pojo.User;
import com.chhavi.repository.UserRepository;

@Repository
public class UserDaoImpl implements UserDao {

    private static final Logger logger = LoggerFactory.getLogger(UserDaoImpl.class);

    private final UserRepository userRepository;

    public UserDaoImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User saveUser(User user) {
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            logger.error("Error saving user to database: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByVerificationOtp(String otp) {
        return userRepository.findByVerificationOtp(otp);
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean mobileExists(String mobile) {
        return userRepository.existsByMobile(mobile);
    }

    @Override
    public boolean voterIdExists(String voterId) {
        return userRepository.existsByVoterId(voterId);
    }
}