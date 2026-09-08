package com.fitplan.rag.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRegistryTest {

    @Test
    void collectsAllRegisteredAgents() {
        AgentRegistry registry = new AgentRegistry(List.of(fake("fitness-planning"), fake("safety-guard")));

        assertThat(registry.all()).extracting(Agent::id).containsExactly("fitness-planning", "safety-guard");
        assertThat(registry.findById("safety-guard")).isPresent();
        assertThat(registry.findById("missing")).isEmpty();
    }

    private static Agent fake(String id) {
        return new Agent() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return id;
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public String systemPrompt() {
                return "";
            }
        };
    }
}
