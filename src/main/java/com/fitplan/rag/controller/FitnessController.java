package com.fitplan.rag.controller;

import com.fitplan.rag.service.FitnessPlanningService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai/fitness")
public class FitnessController {

    private final FitnessPlanningService fitnessPlanningService;

    public FitnessController(FitnessPlanningService fitnessPlanningService) {
        this.fitnessPlanningService = fitnessPlanningService;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam String message,
            @RequestParam String chatId) {
        return fitnessPlanningService.streamPlan(message, chatId);
    }

    @PostMapping(value = "/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agent(@RequestBody AgentChatRequest request) {
        return fitnessPlanningService.streamPlan(request.message(), request.chatId());
    }

    public record AgentChatRequest(String message, String chatId) {
    }
}