package com.oddiya.controller;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.jupiter.api.DisplayName;

/**
 * Detailed Controller Test Suite
 * 
 * This suite includes comprehensive unit tests for each controller with:
 * - @WebMvcTest and MockMvc for REST endpoint testing
 * - Request/response validation with @Valid annotations
 * - Security testing with @WithMockUser for authenticated endpoints
 * - HTTP status codes, response bodies, headers, content types testing
 * - Error responses and exception handling testing
 */
@Suite
@SelectClasses({
    AuthControllerTest.class,
    TravelPlanControllerTest.class,
    UserControllerTest.class,
    PlaceControllerTest.class
})
@DisplayName("Detailed Controller Layer Test Suite - Comprehensive Unit Tests")
public class DetailedControllerTestSuite {
    // This suite will run all detailed controller tests with comprehensive coverage
}