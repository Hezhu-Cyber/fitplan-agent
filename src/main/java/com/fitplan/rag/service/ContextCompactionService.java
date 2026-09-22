package com.fitplan.rag.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** LLM summarizer for context compaction, with a three-failure circuit breaker. */
@Service
public class ContextCompactionService {

    private final ChatClient client;
    private final String model;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    public ContextCompactionService(
            org.springframework.ai.chat.model.ChatModel chatModel,
            @Value("${fitplan.memory.compaction-model:${fitplan.memory.selector-model:qwen3.7-flash}}") String model) {
        this.client = ChatClient.builder(chatModel).build();
        this.model = model;
    }

    public String summarize(List<Message> messages, boolean emergency) {
        if (consecutiveFailures.get() >= 3) {
            return "";
        }
        StringBuilder transcript = new StringBuilder();
        for (Message message : messages) {
            if (message.getText() != null && !message.getText().isBlank()) {
                transcript.append(message.getMessageType()).append(": ")
                        .append(message.getText()).append("\n\n");
            }
        }
        try {
            String result = client.prompt()
                    .system("你是对话上下文压缩器。请把历史健身对话压缩成结构化、可继续工作的摘要。"
                            + "保留用户明确事实、目标、偏好、限制、已完成训练、未完成事项、当前计划和关键决策。"
                            + "不要编造；不要保留寒暄；不要输出分析过程。使用中文小标题和要点。")
                    .user("压缩级别：" + (emergency ? "紧急" : "主动")
                            + "\n历史消息：\n" + transcript)
                    .options(OpenAiChatOptions.builder().model(model).temperature(0.0).maxTokens(900)
                            .timeout(Duration.ofSeconds(15)).maxRetries(1))
                    .call().content();
            if (result == null || result.isBlank()) {
                throw new IllegalStateException("empty compaction summary");
            }
            consecutiveFailures.set(0);
            return result.trim();
        } catch (RuntimeException exception) {
            consecutiveFailures.incrementAndGet();
            return "";
        }
    }
}
