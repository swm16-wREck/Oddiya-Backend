package com.oddiya.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Basic integration test to verify test configuration works
 * This test should pass even without PostGIS
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
public class BasicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void contextLoads() {
        // This test verifies that the Spring context loads successfully
    }

    @Test
    public void healthCheckShouldReturnOk() throws Exception {
        // Test that the health endpoint is accessible
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    public void apiHealthCheckShouldWork() throws Exception {
        // Test the API health endpoint
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }
}