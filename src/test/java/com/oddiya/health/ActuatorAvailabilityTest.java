package com.oddiya.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.boot.actuator.info.Info;
import org.springframework.boot.actuator.info.InfoContributor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to verify that Spring Boot Actuator classes are available
 */
class ActuatorAvailabilityTest {

    @Test
    void testHealthClassesAvailable() {
        Health health = Health.up().build();
        assertNotNull(health);
        
        HealthIndicator indicator = () -> Health.up().build();
        assertNotNull(indicator);
    }
    
    @Test
    void testInfoClassesAvailable() {
        Info.Builder builder = new Info.Builder();
        assertNotNull(builder);
        
        InfoContributor contributor = infoBuilder -> {};
        assertNotNull(contributor);
    }
}