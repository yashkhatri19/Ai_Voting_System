package com.chhavi.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.chhavi.pojo.PasswordResetOtp;
import com.chhavi.pojo.User;

@Repository
public interface PasswordResetOtpRepository extends MongoRepository<PasswordResetOtp, String> {
    Optional<PasswordResetOtp> findByUserAndUsedFalse(User user);
    List<PasswordResetOtp> findByUser(User user);
}
