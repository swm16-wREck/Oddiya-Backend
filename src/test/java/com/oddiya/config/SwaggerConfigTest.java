package com.oddiya.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for SwaggerConfig configuration class.
 * Tests OpenAPI documentation configuration, security schemes, and server setup.
 */
@SpringBootTest
@ActiveProfiles("test")
class SwaggerConfigTest {
    
    @Autowired
    private SwaggerConfig swaggerConfig;
    
    @Autowired
    private OpenAPI openAPI;
    
    @Test
    void contextLoads() {
        assertNotNull(swaggerConfig);
        assertNotNull(openAPI);
    }
    
    @Test
    void openAPIBeanIsConfigured() {
        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertNotNull(openAPI.getServers());
        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getSecurity());
    }
    
    @Test
    void infoConfigurationIsCorrect() {
        Info info = openAPI.getInfo();
        
        assertNotNull(info);
        assertEquals("Oddiya Travel Planning API", info.getTitle());
        assertEquals("1.0.0", info.getVersion()); // Default version
        assertNotNull(info.getDescription());
        assertTrue(info.getDescription().contains("AI-powered travel planning"));
        assertTrue(info.getDescription().contains("OAuth2 authentication"));
        assertTrue(info.getDescription().contains("AWS Bedrock"));
        assertTrue(info.getDescription().contains("Bearer token authentication"));
    }
    
    @Test
    void contactConfigurationIsCorrect() {
        Info info = openAPI.getInfo();
        Contact contact = info.getContact();
        
        assertNotNull(contact);
        assertEquals("Oddiya API Support", contact.getName());
        assertEquals("api-support@oddiya.com", contact.getEmail());
    }
    
    @Test
    void licenseConfigurationIsCorrect() {
        Info info = openAPI.getInfo();
        License license = info.getLicense();
        
        assertNotNull(license);
        assertEquals("Apache 2.0", license.getName());
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0.html", license.getUrl());
    }
    
    @Test
    void serversConfigurationIsCorrect() {
        List<Server> servers = openAPI.getServers();
        
        assertNotNull(servers);
        assertEquals(3, servers.size());
        
        // Production server
        Server prodServer = servers.get(0);
        assertEquals("https://api.oddiya.com/api/v1", prodServer.getUrl());
        assertEquals("Production server", prodServer.getDescription());
        
        // Test server
        Server testServer = servers.get(1);
        assertEquals("https://api-test.oddiya.com/api/v1", testServer.getUrl());
        assertEquals("Test server", testServer.getDescription());
        
        // Local server
        Server localServer = servers.get(2);
        assertEquals("http://localhost:8080/api/v1", localServer.getUrl()); // Default port
        assertEquals("Local development", localServer.getDescription());
    }
    
    @Test
    void securityRequirementIsConfigured() {
        List<SecurityRequirement> security = openAPI.getSecurity();
        
        assertNotNull(security);
        assertFalse(security.isEmpty());
        
        SecurityRequirement securityRequirement = security.get(0);
        assertTrue(securityRequirement.containsKey("bearerAuth"));
    }
    
    @Test
    void securitySchemeIsConfigured() {
        SecurityScheme bearerAuth = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        
        assertNotNull(bearerAuth);
        assertEquals(SecurityScheme.Type.HTTP, bearerAuth.getType());
        assertEquals("bearer", bearerAuth.getScheme());
        assertEquals("JWT", bearerAuth.getBearerFormat());
        assertNotNull(bearerAuth.getDescription());
        assertTrue(bearerAuth.getDescription().contains("JWT token"));
        assertTrue(bearerAuth.getDescription().contains("login endpoint"));
    }
    
    @Test
    void componentsAreConfigured() {
        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getComponents().getSecuritySchemes());
        assertTrue(openAPI.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
    }
    
    /**
     * Test SwaggerConfig with custom properties
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "server.port=9090",
        "app.version=2.0.0"
    })
    @ActiveProfiles("test")
    static class SwaggerConfigCustomPropertiesTest {
        
        @Autowired
        private OpenAPI openAPI;
        
        @Test
        void customServerPortIsUsed() {
            List<Server> servers = openAPI.getServers();
            
            assertNotNull(servers);
            Server localServer = servers.stream()
                .filter(server -> server.getDescription().equals("Local development"))
                .findFirst()
                .orElse(null);
            
            assertNotNull(localServer);
            assertEquals("http://localhost:9090/api/v1", localServer.getUrl());
        }
        
        @Test
        void customAppVersionIsUsed() {
            Info info = openAPI.getInfo();
            
            assertNotNull(info);
            assertEquals("2.0.0", info.getVersion());
        }
    }
    
    @Test
    void swaggerConfigBeanExists() {
        SwaggerConfig config = swaggerConfig;
        assertNotNull(config);
        
        // Verify it's a proper configuration class
        assertTrue(config.getClass().isAnnotationPresent(org.springframework.context.annotation.Configuration.class));
    }
    
    @Test
    void customOpenAPIMethodExists() throws NoSuchMethodException {
        // Verify the customOpenAPI method exists and is a bean method
        var method = SwaggerConfig.class.getMethod("customOpenAPI");
        assertNotNull(method);
        assertTrue(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class));
        assertEquals(OpenAPI.class, method.getReturnType());
    }
    
    @Test
    void allExpectedFieldsArePresent() {
        // Comprehensive check that all expected fields are configured
        Info info = openAPI.getInfo();
        List<Server> servers = openAPI.getServers();
        
        // Info fields
        assertNotNull(info.getTitle());
        assertNotNull(info.getVersion());
        assertNotNull(info.getDescription());
        assertNotNull(info.getContact());
        assertNotNull(info.getLicense());
        
        // Servers
        assertEquals(3, servers.size());
        
        // Security
        assertNotNull(openAPI.getSecurity());
        assertNotNull(openAPI.getComponents());
        
        // Security scheme
        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertNotNull(scheme);
        assertNotNull(scheme.getType());
        assertNotNull(scheme.getScheme());
        assertNotNull(scheme.getBearerFormat());
        assertNotNull(scheme.getDescription());
    }
    
    @Test
    void descriptionContainsKeyFeatures() {
        String description = openAPI.getInfo().getDescription();
        
        assertNotNull(description);
        
        // Check that description mentions key features
        assertTrue(description.contains("AI-powered travel planning"));
        assertTrue(description.contains("AWS Bedrock"));
        assertTrue(description.contains("OAuth2 authentication"));
        assertTrue(description.contains("Google, Apple"));
        assertTrue(description.contains("Photo to video"));
        assertTrue(description.contains("Place discovery"));
        assertTrue(description.contains("Real-time notifications"));
        assertTrue(description.contains("Bearer token"));
        assertTrue(description.contains("Authorization header"));
    }
}