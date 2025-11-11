package com.example.reminder.security;

import com.example.reminder.model.User;
import com.example.reminder.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AuthContext {
    private  final UserRepository userRepository;

    public AuthContext(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Email not found."+email));
    }

    public boolean isAdmin() {
        return getCurrentUser().getRolesAsString().contains("ADMIN");
    }

    public boolean isUser() {
        return getCurrentUser().getRolesAsString().contains("USER");
    }
}
