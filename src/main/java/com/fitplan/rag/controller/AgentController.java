package com.fitplan.rag.controller;

import com.fitplan.rag.agent.Agent;
import com.fitplan.rag.service.FitnessPlanningService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 暴露双 Agent 注册信息，便于演示与审计：
 * GET /api/ai/agents 返回健身规划 Agent 与安全审查 Agent 的公开元数据与工具列表。
 * 系统提示词属于服务端实现细节，不通过公共接口返回。
 */
@RestController
@RequestMapping("/ai/agents")
public class AgentController {

    private final FitnessPlanningService fitnessPlanningService;

    public AgentController(FitnessPlanningService fitnessPlanningService) {
        this.fitnessPlanningService = fitnessPlanningService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AgentInfo> agents() {
        return fitnessPlanningService.agents().stream()
                .map(AgentInfo::from)
                .toList();
    }

    public record AgentInfo(
            String id,
            String name,
            String description,
            List<String> toolNames) {

        static AgentInfo from(Agent agent) {
            return new AgentInfo(
                    agent.id(),
                    agent.name(),
                    agent.description(),
                    agent.toolNames());
        }
    }
}
