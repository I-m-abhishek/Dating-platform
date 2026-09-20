package com.dating.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Dating Platform API.
 *
 * <p>Architecture: feature-sliced, layered modules. Every feature package owns its
 * {@code controller} (HTTP edge), {@code service} (business rules & transactions),
 * {@code repository} (persistence), {@code entity} (domain model) and {@code dto}
 * (transport contracts). Nothing outside a feature package may reference its entities;
 * cross-feature communication happens through service interfaces and DTOs.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableScheduling
@EnableAsync
public class DatingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatingPlatformApplication.class, args);
    }
}
