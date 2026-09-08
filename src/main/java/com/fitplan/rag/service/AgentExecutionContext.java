package com.fitplan.rag.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-request context shared between the agent's tools and the streaming response.
 * Collects the executed tool-call trail and the knowledge sources actually retrieved.
 */
public final class AgentExecutionContext {

    public static final String TOOL_CONTEXT_KEY = "fitplanAgentExecution";
    private static final int MAX_TOOL_CALLS = 8;

    private final String chatId;
    private final Set<String> sourceFiles = new LinkedHashSet<>();
    private final List<String> toolCalls = new CopyOnWriteArrayList<>();
    private final List<String> contexts = new CopyOnWriteArrayList<>();
    private final AtomicInteger toolCallCount = new AtomicInteger();

    public AgentExecutionContext(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
        this.chatId = chatId;
    }

    public String chatId() {
        return chatId;
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
}