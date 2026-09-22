package com.fitplan.rag.service;

import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever.RetrievedKnowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-request context shared between the agent tools and the response. */
public final class AgentExecutionContext {

    public static final String TOOL_CONTEXT_KEY = "fitplanAgentExecution";
    private static final int MAX_TOOL_CALLS = 8;
    private static final UUID LEGACY_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UUID ownerId;
    private final String chatId;
    private final String operationId;
    private final Set<String> sourceFiles = new LinkedHashSet<>();
    private final List<String> toolCalls = new CopyOnWriteArrayList<>();
    private final List<String> contexts = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, List<RetrievedKnowledge>> knowledgeCache = new ConcurrentHashMap<>();
    private final AtomicInteger toolCallCount = new AtomicInteger();

    public AgentExecutionContext(String chatId) {
        this(LEGACY_OWNER, chatId, UUID.randomUUID().toString());
    }

    public AgentExecutionContext(UUID ownerId, String chatId, String operationId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
        String normalizedChat = chatId.trim();
        if (normalizedChat.length() > 128) {
            throw new IllegalArgumentException("chatId must not exceed 128 characters");
        }
        this.ownerId = ownerId;
        this.chatId = normalizedChat;
        this.operationId = operationId == null || operationId.isBlank()
                ? UUID.randomUUID().toString()
                : operationId.trim();
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String chatId() {
        return chatId;
    }

    public String operationId() {
        return operationId;
    }

    public String conversationId() {
        return ownerId + ":" + chatId;
    }

    public String scopedOperationId(String namespace) {
        try {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest((namespace == null ? "" : namespace).getBytes(StandardCharsets.UTF_8)));
            return operationId + ":" + digest.substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public synchronized void addSource(String filename) {
        if (filename != null && !filename.isBlank()) {
            sourceFiles.add(filename.trim());
        }
    }

    public synchronized Set<String> sourceFiles() {
        return new LinkedHashSet<>(sourceFiles);
    }

    public void recordToolCall(String toolName) {
        int current = toolCallCount.incrementAndGet();
        if (current > MAX_TOOL_CALLS) {
            throw new IllegalStateException("Agent tool call limit exceeded: " + MAX_TOOL_CALLS);
        }
        toolCalls.add(toolName);
    }

    public List<String> toolCalls() {
        return List.copyOf(toolCalls);
    }

    public void addContext(String text) {
        if (text != null && !text.isBlank()) {
            contexts.add(text);
        }
    }

    public List<String> contexts() {
        return List.copyOf(contexts);
    }

    public void cacheKnowledge(String query, List<RetrievedKnowledge> results) {
        if (query == null || query.isBlank() || results == null) {
            return;
        }
        knowledgeCache.put(query.trim(), List.copyOf(results));
    }

    public Optional<List<RetrievedKnowledge>> cachedKnowledge(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(knowledgeCache.get(query.trim()));
    }
}
