package com.dating.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the whole application context.
 *
 * <p>Cheap to run and it catches a surprising amount:
 * <ul>
 *   <li>every bean's constructor dependencies resolve, across all 15 feature slices;</li>
 *   <li>no circular dependencies;</li>
 *   <li>every {@code @ConfigurationProperties} record binds;</li>
 *   <li><b>every JPQL query in every repository parses and validates</b> - Spring Data
 *       checks them at startup, so a typo in an {@code @Query} fails here rather than the
 *       first time a user hits that endpoint;</li>
 *   <li>security, WebSocket, caching and scheduling configuration all initialise.</li>
 * </ul>
 *
 * <p>Native queries are the exception: they are only parsed when executed, so
 * {@code DiscoveryRepository}'s PostgreSQL-specific SQL is not covered here.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the application context loads with every bean wired")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(100);
    }

    @Test
    @DisplayName("every controller, service and repository is registered")
    void allLayersArePresent() {
        assertThat(context.getBeanNamesForAnnotation(org.springframework.web.bind.annotation.RestController.class))
                .hasSizeGreaterThanOrEqualTo(12);
        assertThat(context.getBeanNamesForType(org.springframework.data.repository.Repository.class))
                .hasSizeGreaterThanOrEqualTo(18);
    }
}
