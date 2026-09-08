package com.fitplan.rag.agent;

import java.util.List;

/**
 * FitPlan 中的 Agent 抽象：一个可独立描述、注册与编排的智能体单元。
 * 每个 Agent 都有稳定的 id、面向用户的名称、职责描述、系统提示词以及它暴露的工具。
 */
public interface Agent {

    /** 稳定唯一标识，用于注册表查询与观测。 */
    String id();

    /** 面向用户/简历展示的名称。 */
    String name();

    /** 职责与边界描述。 */
    String description();

    /** 该 Agent 的系统提示词。 */
    String systemPrompt();

    /** 该 Agent 可调用的工具名列表（用于展示与审计）。 */
    default List<String> toolNames() {
        return List.of();
    }
}
