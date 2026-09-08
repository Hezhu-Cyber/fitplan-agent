package com.fitplan.rag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS policy for the local frontend (Vite dev server on another port).
 * Restrict origins in production deployments.
 */
@Configuration
public class CorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfiguration(
            @Value("${fitplan.web.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
            String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
