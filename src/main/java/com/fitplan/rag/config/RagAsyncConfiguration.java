package com.fitplan.rag.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
@EnableAsync
@EnableScheduling
class RagAsyncConfiguration {

    @Bean("ragIndexExecutor")
    AsyncTaskExecutor ragIndexExecutor() {
        return new VirtualThreadTaskExecutor("rag-index-");
    }

    @Bean("webAsyncExecutor")
    AsyncTaskExecutor webAsyncExecutor() {
        return new VirtualThreadTaskExecutor("web-async-");
    }

    @Bean
    WebMvcConfigurer webAsyncConfigurer(
            @Qualifier("webAsyncExecutor") AsyncTaskExecutor webAsyncExecutor) {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setTaskExecutor(webAsyncExecutor);
                configurer.setDefaultTimeout(Duration.ofSeconds(120).toMillis());
            }
        };
    }
}
