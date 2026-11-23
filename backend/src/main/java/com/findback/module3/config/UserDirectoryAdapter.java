package com.findback.module3.config;

import com.findback.module3.ports.CrossModulePorts.ClaimantDirectoryPort;
import edu.findback.user.User;
import edu.findback.user.UserRepository;
import org.springframework.stereotype.Component;

/** Resolves claimant display names from the shared user accounts for the staff review queue. */
@Component
public class UserDirectoryAdapter implements ClaimantDirectoryPort {
    private final UserRepository users;

    public UserDirectoryAdapter(UserRepository users) {
        this.users = users;
    }

    @Override
    public String displayName(Long userId) {
        return users.findById(userId).map(User::getName).orElse("User #" + userId);
    }
}
