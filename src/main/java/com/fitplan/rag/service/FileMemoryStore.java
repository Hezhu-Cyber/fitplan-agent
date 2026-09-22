package com.fitplan.rag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** File-backed long-term memory: one Markdown file per durable user fact. */
@Service
public class FileMemoryStore {

    private static final Pattern SAFE = Pattern.compile("[^a-zA-Z0-9._-]");
    private final Path root;

    public FileMemoryStore(@Value("${fitplan.memory.directory:Data/memory}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String index(UUID ownerId, String chatId) {
        Path dir = directory(ownerId, chatId);
        Path index = dir.resolve("MEMORY.md");
        if (!Files.exists(index)) {
            return "";
        }
        try {
            return Files.readString(index, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read memory index", exception);
        }
    }

    public void save(UUID ownerId, String chatId, String title, String category, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("memory content must not be blank");
        }
        Path dir = directory(ownerId, chatId);
        try {
            Files.createDirectories(dir);
            String id = SAFE.matcher(title == null ? "memory" : title.trim().toLowerCase()).replaceAll("-");
            if (id.isBlank()) {
                id = "memory-" + System.currentTimeMillis();
            }
            Path file = dir.resolve(id + ".md");
            String body = "---\n" + "id: " + id + "\ncategory: " + safe(category) + "\n"
                    + "updated_at: " + Instant.now() + "\n---\n\n" + content.trim() + "\n";
            Files.writeString(file, body, StandardCharsets.UTF_8);
            rebuildIndex(dir);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist memory", exception);
        }
    }

    public List<String> readSelected(UUID ownerId, String chatId, List<String> ids) {
        List<String> selected = new ArrayList<>();
        for (String id : ids) {
            if (id == null || !SAFE.matcher(id).replaceAll("").equals(id)) {
                continue;
            }
            Path file = directory(ownerId, chatId).resolve(id + ".md");
            try {
                if (Files.exists(file)) {
                    selected.add(Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read selected memory", exception);
            }
        }
        return List.copyOf(selected);
    }

    private void rebuildIndex(Path dir) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Memory Index");
        lines.add("");
        try (var files = Files.list(dir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> !path.getFileName().toString().equals("MEMORY.md"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            List<String> head = Files.readAllLines(path, StandardCharsets.UTF_8);
                            String id = path.getFileName().toString().replaceFirst("\\.md$", "");
                            String category = head.stream().filter(line -> line.startsWith("category:"))
                                    .findFirst().orElse("category: general").substring("category:".length()).trim();
                            String preview = id;
                            for (String line : head) {
                                if (!line.isBlank() && !line.startsWith("---") && !line.startsWith("id:")
                                        && !line.startsWith("category:") && !line.startsWith("updated_at:")) {
                                    preview = line.trim();
                                    break;
                                }
                            }
                            lines.add("- id=" + id + " | category=" + category + " | " + preview);
                        } catch (IOException ignored) {
                            // A single malformed memory must not prevent other memories from loading.
                        }
                    });
        }
        Files.write(dir.resolve("MEMORY.md"), lines, StandardCharsets.UTF_8);
    }

    private Path directory(UUID ownerId, String chatId) {
        String owner = ownerId == null ? "anonymous" : ownerId.toString();
        String conversation = SAFE.matcher(FitnessAgentStateRepository.requireChatId(chatId)).replaceAll("-");
        return root.resolve(owner).resolve(conversation).normalize();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "general" : SAFE.matcher(value.trim()).replaceAll("-");
    }
}
