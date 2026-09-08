package com.fitplan.rag.knowledge;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievedSourceFormatterTest {

    @Test
    void formatsUniqueFilenamesInInsertionOrder() {
        Set<String> filenames = new LinkedHashSet<>();
        filenames.add("knowledge-base/documents/strength.md");
        filenames.add("knowledge-base/documents/strength.md");
        filenames.add("C:\\knowledge-base\\documents\\nutrition.md");

        assertThat(filenames).containsExactly(
                "knowledge-base/documents/strength.md",
                "C:\\knowledge-base\\documents\\nutrition.md");
        assertThat(RetrievedSourceFormatter.format(filenames))
                .isEqualTo("\n\n---\n知识来源：`strength.md`、`nutrition.md`");
    }

    @Test
    void explainsWhenNoKnowledgePassesTheThreshold() {
        assertThat(RetrievedSourceFormatter.format(Set.of()))
                .contains("未调用知识检索工具", "未检索到达到阈值的本地知识");
    }
}
