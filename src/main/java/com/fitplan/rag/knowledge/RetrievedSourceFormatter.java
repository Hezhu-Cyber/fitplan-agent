package com.fitplan.rag.knowledge;

import java.util.Collection;
import java.util.stream.Collectors;

/** Formats the retrieved knowledge filenames into the citation footer of an answer. */
public final class RetrievedSourceFormatter {

    private RetrievedSourceFormatter() {
    }

    public static String format(Collection<String> filenames) {
        if (filenames == null || filenames.isEmpty()) {
            return "\n\n---\n知识来源：本轮未调用知识检索工具，或未检索到达到阈值的本地知识。";
        }
        String sources = filenames.stream()
                .map(RetrievedSourceFormatter::displayName)
                .map(name -> "`" + name + "`")
                .collect(Collectors.joining("、"));
        return "\n\n---\n知识来源：" + sources;
    }

    private static String displayName(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash >= 0 ? filename.substring(slash + 1) : filename;
    }
}