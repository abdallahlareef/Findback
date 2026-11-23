package com.findback.module3.e2e;

import edu.findback.user.Role;
import edu.findback.user.User;
import edu.findback.user.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Seeds two staff accounts for the browser tests in /e2e. Registration only ever creates USER
 * accounts, so the admins have to come from somewhere. This class lives in test sources (never in
 * the application jar) and only runs with the "e2e" profile, which the Playwright config sets.
 */
@Configuration
@Profile("e2e")
class E2eSeedData {
    static final String PASSWORD = "E2e-Password-123";
    private static final Map<String, String> ADMINS = Map.of(
            "admin-a@e2e.test", "Admin A (e2e)",
            "admin-b@e2e.test", "Admin B (e2e)");

    @Bean
    ApplicationRunner seedE2eAdmins(UserRepository users, PasswordEncoder encoder) {
        return args -> ADMINS.forEach((email, name) -> {
            if (users.existsByEmail(email)) return;
            User admin = new User();
            admin.setName(name);
            admin.setEmail(email);
            admin.setRole(Role.ADMIN);
            admin.setPasswordHash(encoder.encode(PASSWORD));
            users.save(admin);
        });
    }
}
