package com.fitplan.rag.controller;

import com.fitplan.rag.auth.AuthenticatedUser;
import com.fitplan.rag.service.FitnessPlanningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/ai/fitness")
public class FitnessController {

    private final FitnessPlanningService fitnessPlanningService;

    public FitnessController(FitnessPlanningService fitnessPlanningService) {
        this.fitnessPlanningService = fitnessPlanningService;
    }

    /** Kept for lightweight demos and backwards compatibility. */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam @NotBlank @Size(max = 2000) String message,
            @RequestParam @NotBlank @Size(max = 128) String chatId,
            Authentication authentication) {
        return fitnessPlanningService.streamPlan(ownerId(authentication), message, chatId);
    }

    /** Preferred endpoint: health text stays in the request body instead of URLs and access logs. */
    @PostMapping(value = "/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agent(
            @Valid @RequestBody AgentChatRequest request,
            Authentication authentication) {
        return fitnessPlanningService.streamPlan(ownerId(authentication), request.message(), request.chatId());
    }

    public record AgentChatRequest(
            @NotBlank @Size(max = 2000) String message,
            @NotBlank @Size(max = 128) String chatId) {
    }

    private static UUID ownerId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        throw new IllegalStateException("Authenticated user is required");
    }
}
