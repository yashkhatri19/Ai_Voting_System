package com.chhavi.votingsystem;

import com.chhavi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VotingSystemApplicationTests {

	@Autowired
	private UserRepository userRepository;

	@Test
	void contextLoads() {
		userRepository.findAll().forEach(user -> {
			System.out.println("=========================================");
			System.out.println("USER: " + user.getEmail() + " | ROLE: " + user.getRole() + " | VERIFIED: " + user.isEmailVerified());
			System.out.println("=========================================");
		});
	}

}
