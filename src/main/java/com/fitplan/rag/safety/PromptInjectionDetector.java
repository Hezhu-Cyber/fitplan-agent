package com.fitplan.rag.safety;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic prompt-injection screen for every user-controlled message. */
@Component
public class PromptInjectionDetector {

    private static final int DEFAULT_BLOCK_SCORE = 3;

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(忽略|无视|忘记|放弃|不再遵守|绕过)[^\\n。]{0,16}(指令|规则|约束|提示|设定|要求|系统)"), 3, "instruction-override"),
            new Rule(Pattern.compile("(扮演|你现在是|从现在起你是)[^\\n。]{0,24}(不受(约束|限制)|无限制|开发者模式|任何角色)"), 3, "role-jailbreak"),
            new Rule(Pattern.compile("(ignore|disregard|forget|bypass)\\s+(all\\s+)?(previous|prior|above|your)"), 3, "english-override"),
            new Rule(Pattern.compile("(system\\s*prompt|系统提示词|开发者指令|隐藏指令|初始指令)"), 2, "system-prompt-probe"),
            new Rule(Pattern.compile("(输出|打印|展示|泄露|说出)(你的|系统的)?(提示词|指令|prompt)"), 2, "prompt-leak"),
            new Rule(Pattern.compile("(DAN\\s?模式|越狱|jailbreak|developer\\s*mode)"), 2, "jailbreak"),
            new Rule(Pattern.compile("请?(用中文|英文)?重复(你的|上述)?(指令|提示词)"), 2, "instruction-repeat"));

    public Verdict inspect(String text) {
        if (text == null || text.isBlank()) {
            return Verdict.allowed();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        int score = 0;
        List<String> matches = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(normalized).find()) {
                score += rule.weight();
                matches.add(rule.name());
            }
        }
        return new Verdict(score >= DEFAULT_BLOCK_SCORE, score, List.copyOf(matches));
    }

    public record Verdict(boolean blocked, int score, List<String> matches) {

        static Verdict allowed() {
            return new Verdict(false, 0, List.of());
        }

        public Verdict {
            matches = List.copyOf(matches);
        }
    }

    private record Rule(Pattern pattern, int weight, String name) {
    }
}
