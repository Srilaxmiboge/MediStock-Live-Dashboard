package com.medistock.service;

import com.medistock.dto.InsightDtos.*;
import com.medistock.model.User;
import com.medistock.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

/** The signed-in user's own profile and password. */
@Service
public class AccountService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityService activityService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          ActivityService activityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityService = activityService;
    }

    @Transactional(readOnly = true)
    public ProfileView profile(String username) {
        return toView(find(username));
    }

    @Transactional
    public ProfileView updateProfile(String username, ProfileRequest request) {
        if (request == null) {
            throw badRequest("Nothing to update.");
        }
        String fullName = StringUtils.hasText(request.fullName()) ? request.fullName().trim() : null;
        String email = StringUtils.hasText(request.email()) ? request.email().trim() : null;
        if (fullName != null && fullName.length() > 120) {
            throw badRequest("Name must be 120 characters or fewer.");
        }
        if (email != null && (email.length() > 160 || !EMAIL.matcher(email).matches())) {
            throw badRequest("Enter a valid email address.");
        }
        User user = find(username);
        user.setFullName(fullName);
        user.setEmail(email);
        userRepository.save(user);
        activityService.record("Account", "Updated profile details");
        return toView(user);
    }

    @Transactional
    public void changePassword(String username, PasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.currentPassword())
                || !StringUtils.hasText(request.newPassword())) {
            throw badRequest("Enter your current password and a new password.");
        }
        User user = find(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw badRequest("Current password is incorrect.");
        }
        if (request.newPassword().length() < 8) {
            throw badRequest("New password must be at least 8 characters.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw badRequest("New password must be different from the current one.");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        activityService.record("Account", "Changed account password");
    }

    /** Name shown in the UI: the saved full name, otherwise the capitalised username. */
    public static String displayName(User user) {
        if (StringUtils.hasText(user.getFullName())) {
            return user.getFullName();
        }
        String username = user.getUsername();
        return username.isEmpty() ? username
                : username.substring(0, 1).toUpperCase(Locale.ROOT) + username.substring(1);
    }

    private ProfileView toView(User user) {
        String roleLabel = "ROLE_ADMIN".equals(user.getRole()) ? "Administrator" : "Staff";
        return new ProfileView(user.getUsername(), user.getFullName(), displayName(user), user.getEmail(),
                user.getRole(), roleLabel);
    }

    private User find(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
