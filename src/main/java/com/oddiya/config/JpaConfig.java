package com.oddiya.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import jakarta.annotation.PostConstruct;

/**
 * JPA Configuration that is conditionally enabled based on active profiles.
 * Only activates when NOT using DynamoDB profile to avoid conflicts.
 */
@Configuration
@Profile("!" + ProfileConfiguration.DYNAMODB_PROFILE)
@EnableJpaRepositories(
    basePackages = "com.oddiya.repository",
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.REGEX,
        pattern = "com.oddiya.repository.dynamodb.*"
    )
)
@EnableJpaAuditing
@RequiredArgsConstructor
@Slf4j
public class JpaConfig {
    
    private final Environment environment;
    
    @PostConstruct
    public void logJpaConfiguration() {
        String[] activeProfiles = environment.getActiveProfiles();
        log.info("JPA Configuration activated for profiles: {}", java.util.Arrays.toString(activeProfiles));
        
        // Log JPA-specific configuration
        String dataSourceUrl = environment.getProperty("spring.datasource.url");
        String hibernateDdlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        
        log.info("JPA DataSource URL: {}", maskUrl(dataSourceUrl));
        log.info("Hibernate DDL Auto: {}", hibernateDdlAuto);
        log.info("JPA Repositories enabled in package: com.oddiya.repository");
        log.info("JPA Auditing enabled for entity timestamps");
    }
    
    private String maskUrl(String url) {
        if (url == null) return "null";
        // Mask passwords in connection strings
        return url.replaceAll("password=[^;\\s]+", "password=***");
    }
}