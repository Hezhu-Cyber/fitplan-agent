package com.fitplan.rag.controller;

import com.fitplan.rag.service.FitnessEvalService;
import com.fitplan.rag.service.FitnessEvalService.EvalResult;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测专用 REST 接口：暴露给外部 RAGAS 评测脚本调用。
 * 入参：question；出参：question, contexts, answer, source_files, tool_calls, latency_ms。
 * 业务流式接口（/chat、/agent）不受影响。
 */
@RestController
@Profile("evaluation")
@RequestMapping("/ai/fitness")
public class FitnessEvalController {

    private final FitnessEvalService evalService;

    public FitnessEvalController(FitnessEvalService evalService) {
        this.evalService = evalService;
    }

    @PostMapping("/eval")
    public EvalResult evaluate(@RequestBody EvalRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return evalService.evaluate(request.question(), request.retrievalMode());
    }

    public record EvalRequest(@NotBlank String question, String retrievalMode) {
    }
}
