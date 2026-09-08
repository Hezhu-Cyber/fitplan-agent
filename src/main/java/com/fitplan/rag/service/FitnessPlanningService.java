package com.fitplan.rag.service;

import com.fitplan.rag.agent.Agent;
import com.fitplan.rag.agent.AgentRegistry;
import com.fitplan.rag.agent.FitnessPlanningAgent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 双 Agent 编排器（对外门面）。
 *
 * <p>流水线：
 * 1) 安全审查 Agent（{@code SafetyGuardAgent}）以 {@code FitnessRiskAdvisor} 形式织入
 *    健身规划 Agent 的调用链，先于会话记忆执行；命中风险 fail-closed 拦截——不进入模型、不写入聊天记忆。
 * 2) 放行后由健身规划 Agent（{@link FitnessPlanningAgent}）执行多步工具调用循环并 SSE 流式输出。
 *
 * <p>两个 Agent 均注册于 {@link AgentRegistry}，可通过 {@code GET /api/ai/agents} 查看。
 */
@Service
public class FitnessPlanningService {

    private final FitnessPlanningAgent planningAgent;
    private final AgentRegistry agentRegistry;

    public FitnessPlanningService(FitnessPlanningAgent planningAgent, AgentRegistry agentRegistry) {
        this.planningAgent = planningAgent;
        this.agentRegistry = agentRegistry;
    }

    /** 双 Agent 流水线入口：安全审查放行后，由健身规划 Agent 流式生成回答。 */
    public Flux<String> streamPlan(String message, String chatId) {
        return planningAgent.streamPlan(message, chatId);
    }

    /** 返回当前注册的全部 Agent（健身规划 Agent + 安全审查 Agent）。 */
    public List<Agent> agents() {
        return agentRegistry.all();
    }
}
