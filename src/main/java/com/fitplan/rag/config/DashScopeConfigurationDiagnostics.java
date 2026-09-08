package com.fitplan.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
final class DashScopeConfigurationDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(DashScopeConfigurationDiagnostics.class);

    private final String apiKey;
    private final String baseUrl;
    private final String chatModel;

    DashScopeConfigurationDiagnostics(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.chat.model:}") String chatModel) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    void reportConfiguration() {
        String trimmed = apiKey.trim();
        String keyType = trimmed.startsWith("sk-sp-") ? "plan-key"
                : trimmed.startsWith("sk-ws") ? "workspace-key"
                : trimmed.startsWith("sk-") ? "legacy-key"
                : "unknown";
        log.info("DashScope configuration: baseUrl={}, chatModel={}, keyType={}, keyLength={}, whitespace={}",
                baseUrl, chatModel, keyType, trimmed.length(), !apiKey.equals(trimmed));
    }
}
