package com.oddiya.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP Client Configuration
 * Configures RestTemplate for external API calls
 */
@Slf4j
@Configuration
public class HttpClientConfiguration {

    @Bean
    public RestTemplate restTemplate() {
        log.info("Creating RestTemplate with custom configuration");
        
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setConnectionRequestTimeout(Duration.ofSeconds(10));
        
        return new RestTemplate(factory);
    }
}