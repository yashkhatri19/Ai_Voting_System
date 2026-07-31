package com.chhavi.votingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.chhavi")
@EnableMongoRepositories(basePackages = "com.chhavi.repository")
public class VotingSystemApplication {

    static {
        try {
            java.io.File envFile = new java.io.File(".env");
            if (!envFile.exists()) {
                java.io.File envExampleFile = new java.io.File(".env.example");
                if (envExampleFile.exists()) {
                    java.nio.file.Files.copy(envExampleFile.toPath(), envFile.toPath());
                }
            }
            if (envFile.exists()) {
                io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                        .ignoreIfMalformed()
                        .ignoreIfMissing()
                        .load();
                
                dotenv.entries().forEach(entry -> {
                    // System environment variables and existing system properties take priority
                    if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                        System.setProperty(entry.getKey(), entry.getValue());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Could not load local .env file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(VotingSystemApplication.class, args);
    }
}