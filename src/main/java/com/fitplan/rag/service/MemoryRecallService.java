package com.fitplan.rag.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Uses a small model to select memory-file ids from the always-loaded index. */
@Service
public class MemoryRecallService {

    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9._-]+");
    private final ChatClient selector;
    private final FileMemoryStore store;
    private final String model;

    public MemoryRecallService(
            ChatModel chatModel,
            FileMemoryStore store,
            @Value("${fitplan.memory.selector-model:qwen3.5-flash}") String model) {
        this.selector = ChatClient.builder(chatModel).build();
        this.store = store;
        this.model = model;
    }

    public String recall(java.util.UUID ownerId, String chatId, String query) {
        String index = store.index(ownerId, chatId);
        if (index.isBlank()) {
            return "";
        }
        String selected = selector.prompt()
                .system("你是记忆检索器。只从给定 MEMORY.md 索引中选择与用户问题直接相关的记忆 id。"
                        + "只输出 id，每行一个；没有相关记忆就输出 NONE。不要编造 id。")
                .user("用户问题：\n" + safe(query) + "\n\nMEMORY.md：\n" + index)
                .options(OpenAiChatOptions.builder().model(model).temperature(0.0).maxTokens(120)
                        .timeout(Duration.ofSeconds(8)).maxRetries(1))
                .call().content();
        List<String> ids = Arrays.stream(selected == null ? new String[0] : selected.split("\\R"))
                .map(String::trim).flatMap(line -> ID.matcher(line).results().map(match -> match.group()))
                .distinct().limit(5).toList();
        return store.readSelected(ownerId, chatId, ids).stream()
                .collect(Collectors.joining("\n\n"));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
