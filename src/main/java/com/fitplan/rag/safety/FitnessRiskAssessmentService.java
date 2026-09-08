package com.fitplan.rag.safety;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FitnessRiskAssessmentService {

    private static final List<RiskRule> URGENT_MEDICAL_RULES = List.of(
            rule("chest-pain", "胸痛",
                    "胸(?:口|部)?(?:突然|持续|明显|剧烈|很|非常)?(?:疼痛|闷痛|刺痛|疼|痛)"),
            rule("fainting", "晕厥或失去意识",
                    "晕厥|昏厥|晕倒|失去意识"),
            rule("severe-breathlessness", "异常气短或呼吸困难",
                    "(?:异常|严重|明显|突然)(?:气短|呼吸困难)|喘不上气|呼吸不过来|呼吸困难"),
            rule("palpitations-with-discomfort", "心悸伴不适",
                    "(?:心悸|心慌).{0,8}(?:不适|胸闷|胸痛|头晕|气短)"
                            + "|(?:不适|胸闷|胸痛|头晕|气短).{0,8}(?:心悸|心慌)"),
            rule("acute-injury", "急性创伤",
                    "急性(?:创伤|损伤)|(?:刚|刚刚|新近|昨天|今天).{0,4}(?:扭伤|拉伤|摔伤|撞伤)"),
            rule("marked-swelling", "明显肿胀",
                    "(?:明显|严重|快速|突然)(?:的)?肿胀"),
            rule("unable-to-bear-weight", "无法正常负重",
                    "(?:无法|不能|难以)(?:正常)?(?:负重|站立|走路|行走)"));

    private static final List<RiskRule> MEDICAL_BOUNDARY_RULES = List.of(
            rule("medical-diagnosis", "疾病诊断",
                    "(?:诊断|确诊|判断|确认).{0,24}(?:什么病|哪种病|疾病|伤病|病因)"
                            + "|(?:我|这|它).{0,12}(?:是什么病|得了什么病)"),
            rule("rehabilitation-prescription", "伤病康复处方",
                    "(?:康复|治疗)(?:处方|方案|计划)"
                            + "|(?:制定|安排|提供|给).{0,12}(?:康复|治疗)(?:处方|方案|计划)"));

    private static final Pattern NEGATED_SIGNAL = Pattern.compile(
            "(?:没有|没有出现|并无|无|未出现|未发生|否认|不伴)(?:任何|明显)?$");

    public Optional<RiskBlock> assess(String message) {
        if (message == null || message.isBlank()) {
            return Optional.of(new RiskBlock("empty-input", "请输入需要咨询的健身问题。"));
        }

        String normalized = normalize(message);
        Optional<RiskRule> urgentRule = findFirstNonNegatedMatch(URGENT_MEDICAL_RULES, normalized);
        if (urgentRule.isPresent()) {
            RiskRule rule = urgentRule.get();
            return Optional.of(new RiskBlock(
                    rule.code(),
                    "检测到“" + rule.description() + "”相关的健康风险信息。请立即停止训练，并尽快咨询医生或合格医疗专业人士。"
                            + "如果症状正在发生、加重或伴随明显不适，请联系当地急救服务。"
                            + "在专业评估明确安全范围前，本系统不会制定或调整训练计划，也不提供诊断。"
                            + "\n\n---\n知识来源：`04-recovery-safety.md`"));
        }

        Optional<RiskRule> boundaryRule = findFirstNonNegatedMatch(MEDICAL_BOUNDARY_RULES, normalized);
        if (boundaryRule.isPresent()) {
            RiskRule rule = boundaryRule.get();
            return Optional.of(new RiskBlock(
                    rule.code(),
                    "该请求涉及“" + rule.description() + "”。本系统只提供面向普通健康成年人的一般健身教育，"
                            + "不能进行疾病诊断或提供伤病康复处方。请由医生或合格专业人士完成评估；"
                            + "在明确安全范围前，不建议自行增加训练负荷。"
                            + "\n\n---\n知识来源：`04-recovery-safety.md`"));
        }
        return Optional.empty();
    }

    private static Optional<RiskRule> findFirstNonNegatedMatch(List<RiskRule> rules, String message) {
        return rules.stream()
                .filter(rule -> hasNonNegatedMatch(rule.pattern(), message))
                .findFirst();
    }

    private static boolean hasNonNegatedMatch(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            int prefixStart = Math.max(0, matcher.start() - 8);
            String prefix = message.substring(prefixStart, matcher.start());
            if (!NEGATED_SIGNAL.matcher(prefix).find()) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String message) {
        return Normalizer.normalize(message, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private static RiskRule rule(String code, String description, String regex) {
        return new RiskRule(code, description, Pattern.compile(regex));
    }

    private record RiskRule(String code, String description, Pattern pattern) {
    }

    public record RiskBlock(String reason, String response) {
    }
}
