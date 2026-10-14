package com.careerconnect.auth.service;

import com.careerconnect.auth.model.User;
import com.careerconnect.auth.repository.UserRepository;
import com.careerconnect.common.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminUserInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:admin@careerconnect.com}") String adminEmail,
            @Value("${app.admin.password:Admin@123456}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail(adminEmail)) {
            String hash = passwordEncoder.encode(adminPassword);
            User admin = User.createNew(adminEmail, hash, Role.ADMIN);
            userRepository.save(admin);
            log.info("Initialized default administrator account: {}", adminEmail);
        }
    }
}
