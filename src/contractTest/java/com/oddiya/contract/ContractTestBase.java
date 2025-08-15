package com.oddiya.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddiya.service.*;
import com.oddiya.config.TestSecurityConfig;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for Spring Cloud Contract tests
 * Provides common setup and mock beans for all contract verifications
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMessageVerifier
@ActiveProfiles("contract-test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:15://localhost/contract-testdb?TC_INITSCRIPT=test-init.sql&TC_DAEMON=true",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "app.aws.enabled=false",
    "app.supabase.enabled=false",
    "logging.level.org.springframework.cloud.contract=DEBUG"
})
@Import(TestSecurityConfig.class)
public abstract class ContractTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock all external dependencies
    @MockBean
    private AuthService authService;
    
    @MockBean
    private TravelPlanService travelPlanService;
    
    @MockBean
    private PlaceService placeService;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private JwtService jwtService;
    
    @MockBean
    private OAuthService oAuthService;
    
    @MockBean
    private SupabaseService supabaseService;
    
    @MockBean
    private AIRecommendationService aiRecommendationService;

    @BeforeEach
    public void setup() {
        RestAssuredMockMvc.webAppContextSetup(webApplicationContext);
    }
    
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}