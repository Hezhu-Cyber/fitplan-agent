package com.fitplan.rag.knowledge;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ragIndex")
class RagIndexHealthIndicator implements HealthIndicator {

    private final RagIndexRepository repository;

    RagIndexHealthIndicator(RagIndexRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        try {
            if (repository.hasSuccessfulIndex()) {
                return Health.up().withDetail("index", "available").build();
            }
            return Health.outOfService()
                    .withDetail("index", "no successfully indexed sources")
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception).withDetail("index", "database unavailable").build();
        }
    }
}
