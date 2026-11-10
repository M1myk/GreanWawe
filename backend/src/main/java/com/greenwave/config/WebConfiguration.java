package com.greenwave.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public WebConfiguration(@Value("${greenwave.cors.allowed-origins}") String origins) {
        allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (Arrays.asList(allowedOrigins).contains("*")) {
            throw new IllegalArgumentException("Explicit CORS origins are required");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
