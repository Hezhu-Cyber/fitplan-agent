package com.fitplan.rag.knowledge;

import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever.RetrievedKnowledge;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/** Runs retrieval before generation so unsupported questions can be refused deterministically. */
@Service
public class KnowledgePreflightService {

    private static final Pattern KNOWLEDGE_TOPIC = Pattern.compile(
            "训练|健身|运动|力量|有氧|跑步|深蹲|硬拉|卧推|推举|拉伸|增肌|减脂|塑形|体能|"
                    + "RPE|RIR|组数|次数|热身|恢复|疼痛|伤病|康复|关节炎|腰突|肩袖|颈椎|骨质疏松|孕期|"
                    + "fitness|workout|training|exercise|strength|cardio", Pattern.CASE_INSENSITIVE);

    private final HybridFitnessKnowledgeRetriever retriever;
    private final FitnessQueryRewriter queryRewriter;

    public KnowledgePreflightService(
            HybridFitnessKnowledgeRetriever retriever,
            FitnessQueryRewriter queryRewriter) {
        this.retriever = retriever;
        this.queryRewriter = queryRewriter;
    }

    public Assessment assess(String question) {
        if (!requiresKnowledge(question)) {
            return Assessment.notRequired();
        }
        try {
            String retrievalQuery = queryRewriter.rewrite(question);
            List<RetrievedKnowledge> results = retriever.retrieve(question, retrievalQuery);
            int contextChars = results.stream().mapToInt(result -> result.text().length()).sum();
            if (results.isEmpty() || contextChars < 120) {
                return Assessment.refused("现有本地知识库不足以可靠回答该问题。"
                        + "为避免无依据建议，本次不生成训练方案。请补充具体场景，或先咨询医生/合格专业人士。");
            }
            return Assessment.grounded(results, formatContext(results));
        } catch (RuntimeException exception) {
            return Assessment.unavailable("当前无法完成知识库核验，为避免生成无依据建议，请稍后重试。");
        }
    }

    public boolean requiresKnowledge(String question) {
        return question != null && KNOWLEDGE_TOPIC.matcher(question).find();
    }

    private static String formatContext(List<RetrievedKnowledge> results) {
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (RetrievedKnowledge result : results) {
            context.append("[预检索来源 ").append(index++).append("] ")
                    .append(result.filename()).append(" / chunk ").append(result.chunkIndex())
                    .append(" / score ").append(String.format(java.util.Locale.ROOT, "%.4f", result.relevanceScore()))
                    .append('\n').append(result.text()).append("\n\n");
        }
        return context.toString().trim();
    }

    public record Assessment(
            boolean required,
            boolean grounded,
            boolean unavailable,
            List<RetrievedKnowledge> results,
            String context,
            String refusal) {

        static Assessment notRequired() {
            return new Assessment(false, true, false, List.of(), "", "");
        }

        static Assessment grounded(List<RetrievedKnowledge> results, String context) {
            return new Assessment(true, true, false, List.copyOf(results), context, "");
        }

        static Assessment refused(String refusal) {
            return new Assessment(true, false, false, List.of(), "", refusal);
        }

        static Assessment unavailable(String refusal) {
            return new Assessment(true, false, true, List.of(), "", refusal);
        }
    }
}
