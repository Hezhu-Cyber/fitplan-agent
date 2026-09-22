package com.fitplan.rag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Archives full oversized tool results so context trimming does not destroy data. */
@Service
public class ContextArtifactStore {
    private final Path root;

    public ContextArtifactStore(@Value("${fitplan.memory.artifact-directory:Data/memory-artifacts}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public String archive(String conversationId, String content) {
        try {
            String safeConversation = conversationId.replaceAll("[^a-zA-Z0-9._-]", "-");
            Path dir = root.resolve(safeConversation);
            Files.createDirectories(dir);
            Path file = dir.resolve(Instant.now().toEpochMilli() + ".txt");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to archive context artifact", exception);
        }
    }

    public String read(String path) {
        try {
            Path file = Path.of(path).toAbsolutePath().normalize();
            if (!file.startsWith(root) || !Files.exists(file)) {
                throw new IllegalArgumentException("context artifact not found");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read context artifact", exception);
        }
    }
}
