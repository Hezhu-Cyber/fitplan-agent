package com.fitplan.rag.controller;

import com.fitplan.rag.auth.AuthenticatedUser;
import com.fitplan.rag.config.ApiExceptionHandler;
import com.fitplan.rag.service.FitnessPlanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FitnessControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private FitnessPlanningService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(FitnessPlanningService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FitnessController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void rejectsBlankMessageBeforeCallingTheAgent() throws Exception {
        mockMvc.perform(post("/ai/fitness/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"","chatId":"valid-chat"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        verifyNoInteractions(service);
    }

    @Test
    void acceptsValidStreamingRequest() throws Exception {
        when(service.streamPlan(OWNER_ID, "RPE 8 是什么", "chat-1"))
                .thenReturn(Flux.just("RPE 8 约保留 2 次。"));

        mockMvc.perform(post("/ai/fitness/agent")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"RPE 8 是什么","chatId":"chat-1"}
                                """))
                .andExpect(status().isOk());
    }


    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(OWNER_ID, "owner@example.com", "Owner"),
                "token",
                List.of());
    }
}
