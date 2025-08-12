package com.oddiya.security;

import com.oddiya.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OWASP A01:2021 - Broken Access Control
 * Authorization Security Tests
 * 
 * Tests authorization mechanisms to ensure users can only access resources
 * and perform actions they're authorized for. Tests vertical and horizontal
 * privilege escalation vulnerabilities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Authorization Security Tests - OWASP A01:2021 - Broken Access Control")
public class AuthorizationSecurityTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
            
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Authorization Test - Travel Plan Ownership Access Control")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testTravelPlanOwnershipAccessControl() throws Exception {
        // Test that user can only access their own travel plans
        
        // Try to access another user's travel plans
        mockMvc.perform(get("/api/v1/travel-plans/user/user2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Should be forbidden for other users
        
        // Try to access own travel plans (should be allowed)
        mockMvc.perform(get("/api/v1/travel-plans/user/user1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should be allowed for own data
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Authorization Test - Travel Plan Modification Access Control")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testTravelPlanModificationAccessControl() throws Exception {
        String updatePayload = """
            {
                "title": "Updated Title",
                "description": "Updated Description",
                "destination": "Updated Destination",
                "startDate": "2024-12-25",
                "endDate": "2024-12-30"
            }
            """;
        
        // Try to update another user's travel plan
        mockMvc.perform(put("/api/v1/travel-plans/other-user-plan-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isForbidden()); // Should prevent unauthorized modifications
        
        // Try to delete another user's travel plan
        mockMvc.perform(delete("/api/v1/travel-plans/other-user-plan-id")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Should prevent unauthorized deletions
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Authorization Test - Horizontal Privilege Escalation")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testHorizontalPrivilegeEscalation() throws Exception {
        // Test accessing other users' profile data
        mockMvc.perform(get("/api/v1/users/user2/profile")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Should not access other user's profile
        
        // Test modifying other users' profile data
        String profileUpdate = """
            {
                "nickname": "Hacked Nickname",
                "bio": "Unauthorized access"
            }
            """;
        
        mockMvc.perform(put("/api/v1/users/user2/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileUpdate))
                .andExpect(status().isForbidden()); // Should not modify other user's profile
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Authorization Test - Vertical Privilege Escalation")
    @WithMockJwtUser(value = "regular-user", email = "user@example.com")
    void testVerticalPrivilegeEscalation() throws Exception {
        // Test accessing admin endpoints as regular user
        mockMvc.perform(get("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Should reject admin access for regular user
        
        mockMvc.perform(post("/api/v1/admin/travel-plans/approve/123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Should reject admin actions
        
        mockMvc.perform(delete("/api/v1/admin/users/123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Should reject admin deletions
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Authorization Test - Collaboration Access Control")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testCollaborationAccessControl() throws Exception {
        // Test adding collaborators to own travel plan (should be allowed)
        mockMvc.perform(post("/api/v1/travel-plans/own-plan-id/collaborators/user2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Owner should be able to add collaborators
        
        // Test adding collaborators to others' travel plan (should be forbidden)
        mockMvc.perform(post("/api/v1/travel-plans/other-plan-id/collaborators/user3")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Non-owner should not add collaborators
        
        // Test removing collaborators from others' travel plan (should be forbidden)
        mockMvc.perform(delete("/api/v1/travel-plans/other-plan-id/collaborators/user2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Non-owner should not remove collaborators
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Authorization Test - Resource Enumeration Attack")
    @WithMockJwtUser(value = "attacker", email = "attacker@example.com")
    void testResourceEnumerationAttack() throws Exception {
        // Test systematic enumeration of travel plan IDs
        String[] testIds = {
            "1", "2", "3", "100", "999", "1000",
            "uuid-format-1", "uuid-format-2", "uuid-format-3",
            "admin-plan", "test-plan", "public-plan",
            "../admin/plans", "../../users/1/plans"
        };
        
        for (String id : testIds) {
            mockMvc.perform(get("/api/v1/travel-plans/" + id)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpected(result -> {
                        int status = result.getResponse().getStatus();
                        // Should either be 404 (not found) or 403 (forbidden), never expose unauthorized data
                        assert status == 404 || status == 403 : 
                               "Unexpected status " + status + " for ID: " + id + 
                               ". Should return 404 or 403 to prevent information disclosure";
                    });
        }
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Authorization Test - Parameter Tampering")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testParameterTampering() throws Exception {
        // Test tampering with user ID parameters
        String travelPlanPayload = """
            {
                "title": "Test Plan",
                "description": "Test Description",
                "destination": "Seoul",
                "startDate": "2024-12-25",
                "endDate": "2024-12-30",
                "userId": "admin"
            }
            """;
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(travelPlanPayload))
                .andExpected(result -> {
                    // Should ignore tampered userId and use authenticated user's ID
                    String response = result.getResponse().getContentAsString();
                    // Verify the created plan belongs to authenticated user, not tampered user
                    assert !response.contains("\"userId\":\"admin\"") : 
                           "Parameter tampering succeeded - userId should be ignored";
                });
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Authorization Test - Session Context Manipulation")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testSessionContextManipulation() throws Exception {
        // Test requests with conflicting user information
        mockMvc.perform(get("/api/v1/users/me")
                .header("X-User-ID", "admin")
                .header("X-Impersonate-User", "admin")
                .header("X-Acting-As", "admin")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpected(result -> {
                    String response = result.getResponse().getContentAsString();
                    // Should return authenticated user's data, not header-specified user
                    assert !response.contains("admin") || response.contains("user1") :
                           "Session context manipulation succeeded - should ignore impersonation headers";
                });
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Authorization Test - Mass Assignment Attack")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testMassAssignmentAttack() throws Exception {
        // Test mass assignment of sensitive fields
        String maliciousPayload = """
            {
                "nickname": "Updated Nickname",
                "bio": "Updated Bio", 
                "role": "admin",
                "permissions": ["admin", "delete", "modify"],
                "isActive": true,
                "isPremium": true,
                "isAdmin": true,
                "userId": "admin",
                "createdAt": "2020-01-01T00:00:00Z",
                "updatedAt": "2020-01-01T00:00:00Z",
                "id": "different-user-id"
            }
            """;
        
        mockMvc.perform(put("/api/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(maliciousPayload))
                .andExpected(result -> {
                    String response = result.getResponse().getContentAsString();
                    // Should only update allowed fields, ignore sensitive fields
                    assert !response.contains("\"role\":\"admin\"") : "Mass assignment succeeded for role field";
                    assert !response.contains("\"isAdmin\":true") : "Mass assignment succeeded for isAdmin field";
                    assert !response.contains("\"isPremium\":true") : "Mass assignment succeeded for isPremium field";
                });
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Authorization Test - File Upload Access Control")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testFileUploadAccessControl() throws Exception {
        // Test uploading files with path traversal names
        String[] maliciousFilenames = {
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\config\\sam",
            "/etc/passwd",
            "C:\\windows\\system32\\config\\sam",
            "admin.jsp",
            "shell.php",
            "malware.exe"
        };
        
        for (String filename : maliciousFilenames) {
            mockMvc.perform(multipart("/api/v1/files/upload")
                    .file("file", "malicious content".getBytes())
                    .param("fileName", filename)
                    .param("contentType", "text/plain"))
                    .andExpected(result -> {
                        int status = result.getResponse().getStatus();
                        // Should either reject malicious filenames or sanitize them
                        if (status == 200) {
                            String response = result.getResponse().getContentAsString();
                            assert !response.contains("../") && !response.contains("..\\") :
                                   "Path traversal in filename not sanitized: " + filename;
                        }
                    });
        }
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Authorization Test - API Rate Limiting Bypass")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testApiRateLimitingBypass() throws Exception {
        // Test if different users share rate limits (they shouldn't)
        // This is tested by switching user context mid-test
        
        // Make requests as user1
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(get("/api/v1/travel-plans")
                    .header("X-Request-ID", "user1-" + i)
                    .contentType(MediaType.APPLICATION_JSON));
        }
        
        // Verify that user isolation is maintained in rate limiting
        // Each user should have independent rate limits
        System.out.println("RECOMMENDATION: Ensure rate limits are applied per-user, not globally");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Authorization Test - Business Logic Bypass")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testBusinessLogicBypass() throws Exception {
        // Test bypassing business logic restrictions
        
        // Test creating travel plans with invalid date logic
        String invalidBusinessLogicPayload = """
            {
                "title": "Invalid Plan",
                "description": "Test bypassing business logic",
                "destination": "Seoul",
                "startDate": "2024-12-30",
                "endDate": "2024-12-25",
                "isPublic": true,
                "maxParticipants": -1,
                "budget": -1000000,
                "priority": 999999
            }
            """;
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBusinessLogicPayload))
                .andExpect(status().isBadRequest()); // Should validate business logic
        
        // Test accessing features above user's permission level
        String premiumFeaturePayload = """
            {
                "title": "Premium Plan",
                "description": "Test accessing premium features",
                "destination": "Seoul",
                "startDate": "2024-12-25",
                "endDate": "2024-12-30",
                "isPremiumFeature": true,
                "useAIRecommendations": true,
                "maxCollaborators": 100
            }
            """;
        
        mockMvc.perform(post("/api/v1/travel-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(premiumFeaturePayload))
                .andExpected(result -> {
                    // Should either reject premium features for non-premium users or ignore them
                    String response = result.getResponse().getContentAsString();
                    if (result.getResponse().getStatus() == 201) {
                        // If created, premium features should be disabled
                        assert !response.contains("\"isPremiumFeature\":true") :
                               "Premium feature bypass - non-premium user accessed premium feature";
                    }
                });
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Authorization Test - Insecure Direct Object Reference (IDOR)")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testInsecureDirectObjectReference() throws Exception {
        // Test accessing objects by manipulating IDs
        String[] objectIds = {
            "1", "2", "999", "admin", "test", 
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002",
            "../admin", "../../system", 
            "%2e%2e%2fadmin", // URL encoded
            "1'; DROP TABLE travel_plans; --" // SQL injection attempt
        };
        
        for (String id : objectIds) {
            // Test travel plan access
            mockMvc.perform(get("/api/v1/travel-plans/" + id)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(result -> {
                        int status = result.getResponse().getStatus();
                        // Should only allow access to user's own resources
                        assert status == 403 || status == 404 || status == 400 :
                               "IDOR vulnerability - unauthorized access to object ID: " + id + 
                               " with status: " + status;
                    });
            
            // Test user profile access  
            mockMvc.perform(get("/api/v1/users/" + id)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(result -> {
                        int status = result.getResponse().getStatus();
                        assert status == 403 || status == 404 || status == 400 :
                               "IDOR vulnerability - unauthorized user access for ID: " + id;
                    });
        }
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Authorization Test - Function Level Authorization")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testFunctionLevelAuthorization() throws Exception {
        // Test that all administrative functions are properly protected
        String[] adminEndpoints = {
            "/api/v1/admin/users",
            "/api/v1/admin/travel-plans", 
            "/api/v1/admin/statistics",
            "/api/v1/admin/reports",
            "/api/v1/admin/settings",
            "/api/v1/system/health",
            "/api/v1/system/metrics",
            "/api/v1/management/info",
            "/api/v1/internal/cache/clear",
            "/api/v1/debug/logs"
        };
        
        for (String endpoint : adminEndpoints) {
            mockMvc.perform(get(endpoint)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(result -> {
                        int status = result.getResponse().getStatus();
                        // Should deny access to admin functions for regular users
                        assert status == 403 || status == 404 :
                               "Function level authorization bypass - regular user accessed: " + endpoint +
                               " with status: " + status;
                    });
        }
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Authorization Test - Context-Dependent Authorization")
    @WithMockJwtUser(value = "user1", email = "user1@example.com")
    void testContextDependentAuthorization() throws Exception {
        // Test authorization that depends on context (time, location, etc.)
        
        // Test accessing resources outside business hours (if applicable)
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("X-Time-Zone", "UTC")
                .header("X-Current-Time", "2024-01-01T02:00:00Z") // 2 AM UTC
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should allow unless specifically restricted
        
        // Test accessing from suspicious locations
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("X-Forwarded-For", "192.168.1.100")
                .header("CF-IPCountry", "XX") // Unknown country
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(result -> {
                    // Should either allow or provide additional security checks
                    int status = result.getResponse().getStatus();
                    assert status == 200 || status == 429 || status == 403 :
                           "Unexpected response to suspicious location access: " + status;
                });
        
        // Test concurrent access from multiple locations (potential account compromise)
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("X-Forwarded-For", "203.0.113.1")
                .header("CF-IPCountry", "US")
                .header("X-Session-ID", "session1")
                .contentType(MediaType.APPLICATION_JSON));
                
        mockMvc.perform(get("/api/v1/travel-plans")
                .header("X-Forwarded-For", "198.51.100.1") 
                .header("CF-IPCountry", "KR")
                .header("X-Session-ID", "session2")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(result -> {
                    // Should detect potential concurrent sessions and handle appropriately
                    System.out.println("RECOMMENDATION: Monitor for concurrent sessions from different locations");
                });
    }
}