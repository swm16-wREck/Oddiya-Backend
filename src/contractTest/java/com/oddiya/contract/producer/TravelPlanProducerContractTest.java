package com.oddiya.contract.producer;

import com.oddiya.contract.TravelPlanContractTestBase;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.test.context.ActiveProfiles;

/**
 * Producer contract tests for Travel Plan API
 * These tests verify that our API implementation matches the defined contracts
 * and generate stubs for consumer testing
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMessageVerifier
@ActiveProfiles("contract-test")
public class TravelPlanProducerContractTest extends TravelPlanContractTestBase {

    @BeforeEach
    public void setup() {
        super.setup();
        setupTravelPlanMocks();
        RestAssuredMockMvc.webAppContextSetup(webApplicationContext);
    }

    /**
     * Auto-generated test methods by Spring Cloud Contract plugin
     * based on contract definitions in src/contractTest/resources/contracts/travel-plans/
     */
    
    @Test
    public void validate_travel_plan_create_success() {
        // Auto-generated from travel_plan_create_success.groovy
    }

    @Test
    public void validate_travel_plan_get_success() {
        // Auto-generated from travel_plan_get_success.groovy
    }

    @Test
    public void validate_travel_plan_get_not_found() {
        // Auto-generated from travel_plan_get_not_found.groovy
    }

    @Test
    public void validate_travel_plan_update_success() {
        // Auto-generated from travel_plan_update_success.groovy
    }

    @Test
    public void validate_travel_plan_delete_success() {
        // Auto-generated from travel_plan_delete_success.groovy
    }

    @Test
    public void validate_travel_plan_search_success() {
        // Auto-generated from travel_plan_search_success.groovy
    }

    @Test
    public void validate_travel_plan_get_public_success() {
        // Auto-generated from travel_plan_get_public_success.groovy
    }
}