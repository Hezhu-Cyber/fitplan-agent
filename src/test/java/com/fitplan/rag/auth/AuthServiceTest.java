package com.fitplan.rag.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private AuthRepository repository;
    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuthRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AuthService(repository, passwordEncoder, 30, true);
    }

    @Test
    void loginIssuesSessionForValidCredentials() {
        UUID userId = UUID.randomUUID();
        String password = "StrongPassword-2026";
        AuthRepository.AppUser user = new AuthRepository.AppUser(
                userId, "owner@example.com", passwordEncoder.encode(password), "Owner", true);
        when(repository.findUserByEmail("owner@example.com")).thenReturn(Optional.of(user));

        AuthService.AuthResponse response = service.login("Owner@Example.com", password);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(userId.toString());
        verify(repository).createSession(anyString(), eq(userId), any());
    }

    @Test
    void loginUsesSameFailureForUnknownUser() {
        when(repository.findUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("missing@example.com", "StrongPassword-2026"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
