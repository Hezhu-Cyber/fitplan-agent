package com.fitplan.rag.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Duration sessionDuration;
    private final boolean registrationEnabled;
    private final String dummyPasswordHash;

    public AuthService(
            AuthRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${fitplan.auth.session-days:30}") long sessionDays,
            @Value("${fitplan.auth.registration-enabled:true}") boolean registrationEnabled) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessionDuration = Duration.ofDays(Math.max(1, sessionDays));
        this.registrationEnabled = registrationEnabled;
        this.dummyPasswordHash = passwordEncoder.encode("fitplan-dummy-password");
    }

    @Transactional
    public AuthResponse register(String email, String password, String displayName) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is disabled");
        }
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = displayName == null || displayName.isBlank()
                ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                : displayName.trim();
        try {
            AuthRepository.AppUser user = repository.createUser(
                    normalizedEmail,
                    passwordEncoder.encode(password),
                    normalizedName);
            return issueSession(user);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        AuthRepository.AppUser user = repository.findUserByEmail(normalizeEmail(email)).orElse(null);
        String passwordHash = user == null ? dummyPasswordHash : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (user == null || !user.enabled() || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return issueSession(user);
    }

    public Optional<AuthRepository.SessionUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hashToken(rawToken.trim());
        Optional<AuthRepository.SessionUser> user = repository.findSessionUser(tokenHash);
        user.ifPresent(ignored -> repository.touchSession(tokenHash));
        return user;
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            repository.deleteSession(hashToken(rawToken.trim()));
        }
    }

    public int cleanExpiredSessions() {
        return repository.deleteExpiredSessions();
    }

    private AuthResponse issueSession(AuthRepository.AppUser user) {
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(sessionDuration);
        repository.createSession(hashToken(token), user.id(), expiresAt);
        return new AuthResponse(
                token,
                expiresAt,
                new UserView(user.id().toString(), user.email(), user.displayName()));
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record AuthResponse(String accessToken, Instant expiresAt, UserView user) {
    }

    public record UserView(String id, String email, String displayName) {
    }
}
