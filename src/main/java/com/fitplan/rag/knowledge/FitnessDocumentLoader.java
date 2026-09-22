package com.fitplan.rag.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the reviewed Markdown knowledge corpus (default: {@code Data/knowledge-base}).
 *
 * <p>Every document must carry YAML front-matter with provenance metadata
 * (publisher, canonical URL, license, rights status, ...). Documents with a
 * missing or unapproved license fail closed so that only reviewed, reusable
 * sources ever reach the vector index. The front-matter block is stripped
 * from the chunk text so only the actual content is embedded and retrieved.
 */
@Component
public class FitnessDocumentLoader {

    static final String CHUNK_TYPE = "knowledge";

    private static final Logger log = LoggerFactory.getLogger(FitnessDocumentLoader.class);
    private static final Pattern FIRST_HEADING = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+?)\\s*$");

    private static final Set<String> ALLOWED_LICENSE_IDS = Set.of(
            "US-PD-ODPHP",
            "US-PD-NIH",
            "US-PD-CDC",
            "OGL-UK-3.0",
            "CC-BY-4.0");

    private final ResourcePatternResolver resourcePatternResolver;
    private final String corpusPattern;

    public FitnessDocumentLoader(
            ResourcePatternResolver resourcePatternResolver,
            @Value("${fitplan.rag.curated-corpus-pattern:file:Data/knowledge-base/**/*.md}")
            String corpusPattern) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.corpusPattern = corpusPattern;
    }

    public List<KnowledgeSource> loadSources() {
        try {
            Resource[] resources = corpusPattern.isBlank()
                    ? new Resource[0]
                    : resolveOptionalResources(corpusPattern);
            if (resources.length == 0) {
                throw new IllegalStateException("No Markdown corpus matched " + corpusPattern);
            }
            List<KnowledgeSource> sources = loadMarkdownSources(resources);
            log.info("Loaded {} Markdown fitness sources ({} knowledge chunks)",
                    sources.size(), sources.stream().mapToInt(source -> source.documents().size()).sum());
            return sources;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load fitness knowledge documents", exception);
        }
    }

    private List<KnowledgeSource> loadMarkdownSources(Resource[] resources) throws IOException {
        List<Resource> ordered = new ArrayList<>(List.of(resources));
        ordered.sort(Comparator.comparing(Resource::getDescription));
        List<KnowledgeSource> sources = new ArrayList<>(ordered.size());
        Set<String> sourceIds = new HashSet<>();
        for (Resource resource : ordered) {
            String text = resource.getContentAsString(StandardCharsets.UTF_8);
            String filename = resource.getFilename();
            if (filename == null || text.isBlank()) {
                continue;
            }
            ParsedMarkdown parsed = parseMarkdown(text);
            String sourceId = markdownSourceId(resource, filename);
            if (!sourceIds.add(sourceId)) {
                throw new IllegalStateException("Duplicate Markdown sourceId " + sourceId);
            }
            validateProvenance(parsed.metadata(), resource, sourceId);

            String body = parsed.body();
            String contentHash = sha256(text.getBytes(StandardCharsets.UTF_8));
            String sourceTitle = metadataValue(parsed.metadata(), "title")
                    .orElseGet(() -> documentTitle(body, filename));
            String language = metadataValue(parsed.metadata(), "language").orElse("zh-CN");

            List<MarkdownSection> sections = splitMarkdownSections(body, filename);
            List<Document> documents = new ArrayList<>(sections.size());
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                MarkdownSection section = sections.get(sectionIndex);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("sourceId", sourceId);
                metadata.put("sourceTitle", sourceTitle);
                metadata.put("sectionTitle", section.title());
                metadata.put("sourceChunkIndex", sectionIndex);
                metadata.put("corpusChunkId", UUID.nameUUIDFromBytes(
                        (sourceId + ':' + contentHash + ':' + sectionIndex).getBytes(StandardCharsets.UTF_8)).toString());
                metadata.put("language", language);
                metadata.put("contentHash", contentHash);
                metadata.put("normalizedPath", sourceId);
                metadata.put("chunkType", CHUNK_TYPE);

                copyIfPresent(metadata, parsed.metadata(), "publisher", "publisher");
                copyIfPresent(metadata, parsed.metadata(), "canonical_url", "canonicalUrl");
                copyIfPresent(metadata, parsed.metadata(), "license_id", "licenseId");
                copyIfPresent(metadata, parsed.metadata(), "license_name", "licenseName");
                copyIfPresent(metadata, parsed.metadata(), "license_url", "licenseUrl");
                copyIfPresent(metadata, parsed.metadata(), "rights_status", "rightsStatus");
                copyIfPresent(metadata, parsed.metadata(), "jurisdiction", "jurisdiction");
                copyIfPresent(metadata, parsed.metadata(), "published_at", "publishedAt");
                copyIfPresent(metadata, parsed.metadata(), "authority_tier", "authorityTier");
                copyIfPresent(metadata, parsed.metadata(), "evidence_role", "evidenceRole");
                copyIfPresent(metadata, parsed.metadata(), "guidance_status", "guidanceStatus");
                copyIfPresent(metadata, parsed.metadata(), "source_language", "sourceLanguage");
                copyIfPresent(metadata, parsed.metadata(), "translation_model", "translationModel");

                documents.add(new Document(section.text(), metadata));
            }
            sources.add(new KnowledgeSource(sourceId, filename, contentHash, documents));
        }
        return List.copyOf(sources);
    }

    /** Parses the leading {@code --- ... ---} front-matter block, if present. */
    private static ParsedMarkdown parseMarkdown(String text) {
        if (!text.startsWith("---")) {
            return new ParsedMarkdown(Map.of(), text);
        }
        int closing = text.indexOf("\n---");
        if (closing < 0) {
            return new ParsedMarkdown(Map.of(), text);
        }
        Map<String, String> metadata = new HashMap<>();
        for (String line : text.substring(3, closing).split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            metadata.put(key, value);
        }
        int bodyStart = text.indexOf('\n', closing + 1);
        String body = bodyStart >= 0 ? text.substring(bodyStart + 1) : "";
        return new ParsedMarkdown(Map.copyOf(metadata), body.stripLeading());
    }

    /** Fails closed unless the source license is allowlisted and rights were approved. */
    private static void validateProvenance(Map<String, String> metadata, Resource resource, String sourceId) {
        String license = metadata.get("license_id");
        if (license == null || !ALLOWED_LICENSE_IDS.contains(license)) {
            throw invalid(resource, "license_id is missing or not allowlisted for " + sourceId
                    + " (found: " + license + ")");
        }
        String rights = metadata.get("rights_status");
        if (!"approved".equalsIgnoreCase(rights)) {
            throw invalid(resource, "rights_status must be 'approved' for " + sourceId
                    + " (found: " + rights + ")");
        }
    }

    private static String markdownSourceId(Resource resource, String filename) throws IOException {
        if (resource.isFile()) {
            Path path = resource.getFile().toPath().toAbsolutePath().normalize();
            for (int index = 0; index < path.getNameCount(); index++) {
                if ("knowledge-base".equals(path.getName(index).toString()) && index + 1 < path.getNameCount()) {
                    return path.subpath(index + 1, path.getNameCount()).toString().replace('\\', '/');
                }
            }
        }
        return filename;
    }

    private static String documentTitle(String body, String filename) {
        Matcher matcher = FIRST_HEADING.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : filename.replaceFirst("(?i)\\.md$", "");
    }

    /** Splits Markdown into sections on heading boundaries; heading-only blocks are skipped. */
    private static List<MarkdownSection> splitMarkdownSections(String text, String fallbackTitle) {
        Matcher matcher = HEADING.matcher(text);
        List<MarkdownSection> sections = new ArrayList<>();
        int start = 0;
        String title = fallbackTitle.replaceFirst("(?i)\\.md$", "");
        while (matcher.find()) {
            if (hasBodyContent(text.substring(start, matcher.start()))) {
                sections.add(new MarkdownSection(title, text.substring(start, matcher.start()).trim()));
            }
            start = matcher.start();
            title = matcher.group(2).trim();
        }
        if (start < text.length() && hasBodyContent(text.substring(start))) {
            sections.add(new MarkdownSection(title, text.substring(start).trim()));
        }
        if (sections.isEmpty() && !text.isBlank()) {
            sections.add(new MarkdownSection(title, text.trim()));
        }
        return List.copyOf(sections);
    }

    private static boolean hasBodyContent(String candidate) {
        if (candidate.isBlank()) {
            return false;
        }
        return !HEADING.matcher(candidate).replaceAll("").isBlank();
    }

    private Resource[] resolveOptionalResources(String pattern) throws IOException {
        try {
            return resourcePatternResolver.getResources(pattern);
        } catch (FileNotFoundException exception) {
            return new Resource[0];
        }
    }

    private static Optional<String> metadataValue(Map<String, String> metadata, String key) {
        String value = metadata.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static void copyIfPresent(
            Map<String, Object> destination,
            Map<String, String> source,
            String sourceKey,
            String destinationKey) {
        metadataValue(source, sourceKey).ifPresent(value -> destination.put(destinationKey, value));
    }

    private static IllegalStateException invalid(Resource resource, String message) {
        return new IllegalStateException("Invalid corpus document " + resource.getDescription() + ": " + message);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record ParsedMarkdown(Map<String, String> metadata, String body) {
    }

    private record MarkdownSection(String title, String text) {
    }

    public record KnowledgeSource(
            String sourceId,
            String filename,
            String contentHash,
            List<Document> documents) {

        public KnowledgeSource {
            documents = List.copyOf(documents);
        }
    }
}
