package com.medistock.config;

import com.medistock.model.User;
import com.medistock.repository.UserRepository;
import com.medistock.service.ActivityService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    public CommandLineRunner initDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                          ActivityService activityService) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole("ROLE_ADMIN");
                userRepository.save(admin);
                System.out.println("Default Admin created: admin / admin123");
            }
            // Fill Recent Activity from existing sales/purchases the first time the activity log is empty.
            activityService.backfillIfEmpty();
        };
    }
}
