package com.fitplan.rag.agent;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Agent 注册表：收集 Spring 容器中所有 Agent bean，供编排与观测端点使用。
 * 按注册顺序（LinkedHashMap）稳定排列，保证 /ai/agents 端点展示顺序一致。
 */
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents;

    public AgentRegistry(List<Agent> agentList) {
        this.agents = Collections.unmodifiableMap(agentList.stream().collect(
                Collectors.toMap(
                        Agent::id,
                        agent -> agent,
                        (first, ignored) -> first,
                        LinkedHashMap::new)));
    }

    /** 返回全部注册的 Agent，保持注册顺序。 */
    public List<Agent> all() {
        return List.copyOf(agents.values());
    }

    public Optional<Agent> findById(String id) {
        return Optional.ofNullable(agents.get(id));
    }
}
