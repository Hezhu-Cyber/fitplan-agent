package com.fitplan.rag.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AppUser createUser(String email, String passwordHash, String displayName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO fitplan_app_user(id, email, password_hash, display_name)
                VALUES (?, lower(?), ?, ?)
                """,
                id,
                email,
                passwordHash,
                displayName);
        return new AppUser(id, email.toLowerCase(), passwordHash, displayName, true);
    }

    public Optional<AppUser> findUserByEmail(String email) {
        List<AppUser> users = jdbcTemplate.query(
                """
                SELECT id, email, password_hash, display_name, enabled
                FROM fitplan_app_user
                WHERE lower(email) = lower(?)
                """,
                (rs, rowNum) -> new AppUser(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getBoolean("enabled")),
                email);
        return users.stream().findFirst();
    }

    public void createSession(String tokenHash, UUID userId, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO fitplan_auth_session(token_hash, user_id, expires_at)
                VALUES (?, ?, ?)
                """,
                tokenHash,
                userId,
                Timestamp.from(expiresAt));
    }

    public Optional<SessionUser> findSessionUser(String tokenHash) {
        List<SessionUser> users = jdbcTemplate.query(
                """
                SELECT u.id, u.email, u.display_name
                FROM fitplan_auth_session s
                JOIN fitplan_app_user u ON u.id = s.user_id
                WHERE s.token_hash = ?
                  AND s.expires_at > now()
                  AND u.enabled = true
                """,
                (rs, rowNum) -> new SessionUser(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("display_name")),
                tokenHash);
        return users.stream().findFirst();
    }

    public void touchSession(String tokenHash) {
        jdbcTemplate.update(
                "UPDATE fitplan_auth_session SET last_used_at = now() WHERE token_hash = ?",
                tokenHash);
    }

    public void deleteSession(String tokenHash) {
        jdbcTemplate.update("DELETE FROM fitplan_auth_session WHERE token_hash = ?", tokenHash);
    }

    public int deleteExpiredSessions() {
        return jdbcTemplate.update("DELETE FROM fitplan_auth_session WHERE expires_at < now()");
    }

    public record AppUser(
            UUID id,
            String email,
            String passwordHash,
            String displayName,
            boolean enabled) {
    }

    public record SessionUser(UUID id, String email, String displayName) {
    }
}
