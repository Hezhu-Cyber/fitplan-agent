package com.fitplan.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** PostgreSQL-backed ChatMemoryRepository with per-message TTL. */
@Repository
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private static final TypeReference<List<AssistantMessage.ToolCall>> TOOL_CALLS =
            new TypeReference<>() { };
    private static final TypeReference<List<ToolResponseMessage.ToolResponse>> TOOL_RESPONSES =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> METADATA =
            new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Duration retention;
    private final ContextCompactionService compactionService;
    private final ContextArtifactStore artifactStore;
    private final int compactAtChars;
    private final int emergencyAtChars;
    private final int recentMessagesAfterCompact;

    public JdbcChatMemoryRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ContextCompactionService compactionService,
            ContextArtifactStore artifactStore,
            @Value("${fitplan.memory.retention-days:30}") long retentionDays,
            @Value("${fitplan.memory.compact-at-chars:43200}") int compactAtChars,
            @Value("${fitplan.memory.emergency-at-chars:45600}") int emergencyAtChars,
            @Value("${fitplan.memory.recent-messages-after-compact:12}") int recentMessagesAfterCompact) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.compactionService = compactionService;
        this.artifactStore = artifactStore;
        this.retention = Duration.ofDays(Math.max(1, retentionDays));
        this.compactAtChars = Math.max(1000, compactAtChars);
        this.emergencyAtChars = Math.max(this.compactAtChars, emergencyAtChars);
        this.recentMessagesAfterCompact = Math.max(4, recentMessagesAfterCompact);
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM fitplan_chat_message WHERE expires_at > now()",
                String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbcTemplate.query(
                """
                SELECT message_type, content, metadata, tool_payload
                FROM fitplan_chat_message
                WHERE conversation_id = ? AND expires_at > now()
                ORDER BY id ASC
                """,
                (rs, rowNum) -> readMessage(
                        MessageType.valueOf(rs.getString("message_type")),
                        rs.getString("content"),
                        rs.getString("metadata"),
                        rs.getString("tool_payload")),
                conversationId);
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        jdbcTemplate.update("DELETE FROM fitplan_chat_message WHERE conversation_id = ?", conversationId);
        List<Message> managedMessages = compact(conversationId, messages);
        if (managedMessages.isEmpty()) {
            return;
        }
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(retention));
        List<Object[]> batch = managedMessages.stream()
                .filter(message -> message != null)
                .map(message -> new Object[] {
                        conversationId,
                        message.getMessageType().name(),
                        text(message),
                        json(message.getMetadata()),
                        toolPayload(message),
                        expiresAt
                })
                .toList();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO fitplan_chat_message(
                    conversation_id, message_type, content, metadata, tool_payload, expires_at)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """,
                batch);
    }

    /** Claude-Code-style bounded memory: summarize old messages at 90% and
     * aggressively retain only the tail at 95%. */
    private List<Message> compact(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Message> normalized = messages.stream()
                .filter(message -> message != null)
                .map(message -> boundLargeToolOutput(conversationId, message))
                .toList();
        int totalChars = normalized.stream().mapToInt(this::messageChars).sum();
        if (totalChars < compactAtChars) {
            return normalized;
        }

        boolean emergency = totalChars >= emergencyAtChars;
        int keep = emergency ? Math.max(6, recentMessagesAfterCompact / 2) : recentMessagesAfterCompact;
        int boundary = Math.max(0, normalized.size() - keep);
        List<Message> result = new ArrayList<>();
        if (boundary > 0) {
            result.add(SystemMessage.builder()
                    .text(summary(normalized.subList(0, boundary), emergency))
                    .metadata(Map.of("fitplan.memory", "compressed", "fitplan.memory.threshold",
                            emergency ? "95%" : "90%"))
                    .build());
        }
        result.addAll(normalized.subList(boundary, normalized.size()));
        return result;
    }

    private String summary(List<Message> messages, boolean emergency) {
        String llmSummary = compactionService.summarize(messages, emergency);
        if (!llmSummary.isBlank()) {
            return "[Claude-style 历史上下文摘要；旧消息已移出当前窗口]\n" + llmSummary;
        }
        return fallbackSummary(messages, emergency);
    }

    private String fallbackSummary(List<Message> messages, boolean emergency) {
        StringBuilder summary = new StringBuilder();
        summary.append("[历史对话压缩摘要；旧消息已移出上下文。结构化资料请通过用户画像、训练日志和当前计划工具恢复。]\n");
        summary.append("压缩级别：").append(emergency ? "紧急（95%）" : "主动（90%）").append("\n");
        int remaining = 3600;
        for (Message message : messages) {
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String item = message.getMessageType().name().toLowerCase(Locale.ROOT)
                    + ": " + text.replaceAll("\\s+", " ").trim() + "\n";
            if (item.length() > remaining) {
                summary.append(item, 0, Math.max(0, remaining)).append("…");
                break;
            }
            summary.append(item);
            remaining -= item.length();
            if (remaining <= 0) {
                break;
            }
        }
        return summary.toString();
    }

    private Message boundLargeToolOutput(String conversationId, Message message) {
        if (message.getMessageType() != MessageType.TOOL || message.getText() == null
                || message.getText().length() <= 50_000) {
            return message;
        }
        String preview = message.getText().substring(0, Math.min(2_000, message.getText().length()));
        String artifact = artifactStore.archive(conversationId, message.getText());
        Map<String, Object> metadata = new LinkedHashMap<>(message.getMetadata());
        metadata.put("fitplan.context-artifact", artifact);
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "compressed-tool-output", "read", preview
                                + "\n[完整结果已归档：" + artifact + "]")))
                .metadata(metadata)
                .build();
    }

    private int messageChars(Message message) {
        return message.getText() == null ? 0 : message.getText().length();
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM fitplan_chat_message WHERE conversation_id = ?", conversationId);
    }

    public int deleteExpired() {
        return jdbcTemplate.update("DELETE FROM fitplan_chat_message WHERE expires_at < now()");
    }

    private Message readMessage(MessageType type, String content, String metadataJson, String toolJson) {
        String text = content == null ? "" : content;
        Map<String, Object> metadata = read(metadataJson, METADATA, Map.of());
        return switch (type) {
            case USER -> UserMessage.builder().text(text).metadata(metadata).build();
            case ASSISTANT -> AssistantMessage.builder()
                    .content(text)
                    .properties(metadata)
                    .toolCalls(read(toolJson, TOOL_CALLS, List.of()))
                    .build();
            case SYSTEM -> SystemMessage.builder().text(text).metadata(metadata).build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(read(toolJson, TOOL_RESPONSES, List.of()))
                    .metadata(metadata)
                    .build();
        };
    }

    private String toolPayload(Message message) {
        if (message instanceof AssistantMessage assistant) {
            return json(assistant.getToolCalls());
        }
        if (message instanceof ToolResponseMessage tool) {
            return json(tool.getResponses());
        }
        return "[]";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize chat memory", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize chat memory", exception);
        }
    }

    private static String text(Message message) {
        String text = message.getText();
        return text == null ? "" : text;
    }
}
