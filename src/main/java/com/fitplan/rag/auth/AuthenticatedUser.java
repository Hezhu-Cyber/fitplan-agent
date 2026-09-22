package com.fitplan.rag.auth;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, String displayName) {
}
