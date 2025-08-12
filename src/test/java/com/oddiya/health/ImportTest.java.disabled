package com.oddiya.health;

import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.boot.actuator.info.Info;
import org.springframework.boot.actuator.info.InfoContributor;

/**
 * Simple test to verify actuator imports work
 */
public class ImportTest {
    public void testImports() {
        Health health = Health.up().build();
        HealthIndicator indicator = () -> health;
        InfoContributor contributor = builder -> {};
        Info.Builder builder = new Info.Builder();
    }
}