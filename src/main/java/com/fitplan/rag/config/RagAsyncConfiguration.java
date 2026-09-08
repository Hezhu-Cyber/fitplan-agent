package com.fitplan.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
class RagAsyncConfiguration {

    @Bean("ragIndexExecutor")
    AsyncTaskExecutor ragIndexExecutor() {
        return new VirtualThreadTaskExecutor("rag-index-");
    }
}
