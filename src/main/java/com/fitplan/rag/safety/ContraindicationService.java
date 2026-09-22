package com.fitplan.rag.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic injury/action contraindication catalog backed by classpath JSON. */
@Service
public class ContraindicationService {

    private static final Pattern NEGATED_PREFIX = Pattern.compile(
            "(?:没有|并无|无|未|否认|不伴|并非|不是|排除)(?:任何|明显)?$");

    private final List<Injury> injuries;

    public ContraindicationService(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("safety/contraindications.json").getInputStream()) {
            this.injuries = objectMapper.readValue(input, Catalog.class).injuries();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load contraindication catalog", exception);
        }
    }

    public Assessment assess(String message) {
        String text = normalize(message);
        if (text.isBlank()) {
            return Assessment.none();
        }

        LinkedHashSet<String> matchedInjuries = new LinkedHashSet<>();
        LinkedHashSet<String> forbidden = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();
        boolean blocked = false;

        for (Injury injury : injuries) {
            if (!containsAny(text, injury.aliases())) {
                continue;
            }
            matchedInjuries.add(injury.name());
            for (Restriction restriction : injury.restrictions()) {
                forbidden.add(restriction.exercise());
                forbidden.addAll(restriction.aliases());
                if (containsAny(text, restriction.aliases())) {
                    blocked = true;
                    reasons.add(injury.name() + "与" + restriction.exercise() + "：" + restriction.reason());
                }
            }
        }

        if (matchedInjuries.isEmpty()) {
            return Assessment.none();
        }
        String response = blocked
                ? "根据确定性安全规则，当前问题包含已登记的伤病与禁忌动作组合，系统已停止生成训练建议。"
                + "涉及组合：" + String.join("；", reasons)
                + "。请勿仅凭本系统自行尝试该动作，先由医生或合格康复专业人士评估安全范围，再选择可执行的替代方案。"
                : "";
        return new Assessment(blocked, List.copyOf(matchedInjuries), Set.copyOf(forbidden),
                List.copyOf(reasons), response);
    }

    public boolean containsForbiddenTerm(String answer, Assessment assessment) {
        return assessment.hasInjury() && !assessment.forbiddenTerms().isEmpty()
                && containsAny(normalize(answer), assessment.forbiddenTerms());
    }

    private static boolean containsAny(String normalizedText, Iterable<String> aliases) {
        for (String alias : aliases) {
            String normalizedAlias = normalize(alias);
            if (normalizedAlias.isBlank()) {
                continue;
            }
            int index = normalizedText.indexOf(normalizedAlias);
            while (index >= 0) {
                int prefixStart = Math.max(0, index - 8);
                String prefix = normalizedText.substring(prefixStart, index);
                if (!NEGATED_PREFIX.matcher(prefix).find()) {
                    return true;
                }
                index = normalizedText.indexOf(normalizedAlias, index + normalizedAlias.length());
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public record Assessment(
            boolean blocked,
            List<String> injuries,
            Set<String> forbiddenTerms,
            List<String> reasons,
            String response) {

        static Assessment none() {
            return new Assessment(false, List.of(), Set.of(), List.of(), "");
        }

        public boolean hasInjury() {
            return !injuries.isEmpty();
        }
    }

    private record Catalog(int version, String disclaimer, List<Injury> injuries) {

        private Catalog {
            injuries = injuries == null ? List.of() : List.copyOf(injuries);
        }
    }

    private record Injury(String name, List<String> aliases, List<Restriction> restrictions) {

        private Injury {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            restrictions = restrictions == null ? List.of() : List.copyOf(restrictions);
        }
    }

    private record Restriction(String exercise, List<String> aliases, String reason) {

        private Restriction {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
