package com.fitplan.rag.auth;

import com.fitplan.rag.service.JdbcChatMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(AuthMaintenanceJob.class);

    private final AuthService authService;
    private final JdbcChatMemoryRepository chatMemoryRepository;

    public AuthMaintenanceJob(
            AuthService authService,
            JdbcChatMemoryRepository chatMemoryRepository) {
        this.authService = authService;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @Scheduled(cron = "${fitplan.auth.cleanup-cron:0 0 3 * * *}")
    public void cleanExpiredState() {
        int sessions = authService.cleanExpiredSessions();
        int messages = chatMemoryRepository.deleteExpired();
        if (sessions > 0 || messages > 0) {
            log.info("Removed {} expired sessions and {} expired chat messages", sessions, messages);
        }
    }
}
