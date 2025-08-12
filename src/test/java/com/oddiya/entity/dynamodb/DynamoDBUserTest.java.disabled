package com.oddiya.entity.dynamodb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamoDBUserTest {

    private DynamoDBUser user;

    @BeforeEach
    void setUp() {
        user = new DynamoDBUser();
    }

    @Test
    void constructor_DefaultConstructor_ShouldCreateEmptyUser() {
        // Act
        DynamoDBUser newUser = new DynamoDBUser();

        // Assert
        assertNotNull(newUser);
        assertNull(newUser.getId());
        assertNull(newUser.getEmail());
        assertNull(newUser.getUsername());
        assertNull(newUser.getFullName());
        assertNull(newUser.getProfileImageUrl());
        assertNull(newUser.getProvider());
        assertNull(newUser.getProviderId());
        assertNull(newUser.getPreferences());
        assertNull(newUser.getCreatedAt());
        assertNull(newUser.getUpdatedAt());
        assertFalse(newUser.isActive());
        assertFalse(newUser.isDeleted());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        // Arrange
        String id = "user-123";
        String email = "test@example.com";
        String username = "testuser";
        String fullName = "Test User";
        String profileImageUrl = "https://example.com/avatar.jpg";
        String provider = "google";
        String providerId = "google-123";
        String preferences = "{\"theme\":\"dark\"}";
        LocalDateTime now = LocalDateTime.now();
        boolean active = true;
        boolean deleted = false;

        // Act
        user.setId(id);
        user.setEmail(email);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setProfileImageUrl(profileImageUrl);
        user.setProvider(provider);
        user.setProviderId(providerId);
        user.setPreferences(preferences);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setActive(active);
        user.setDeleted(deleted);

        // Assert
        assertEquals(id, user.getId());
        assertEquals(email, user.getEmail());
        assertEquals(username, user.getUsername());
        assertEquals(fullName, user.getFullName());
        assertEquals(profileImageUrl, user.getProfileImageUrl());
        assertEquals(provider, user.getProvider());
        assertEquals(providerId, user.getProviderId());
        assertEquals(preferences, user.getPreferences());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
        assertTrue(user.isActive());
        assertFalse(user.isDeleted());
    }

    @Test
    void toString_ShouldContainRelevantFields() {
        // Arrange
        user.setId("user-123");
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setFullName("Test User");

        // Act
        String toString = user.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("user-123"));
        assertTrue(toString.contains("test@example.com"));
        assertTrue(toString.contains("testuser"));
        assertTrue(toString.contains("Test User"));
    }

    @Test
    void equals_SameId_ShouldReturnTrue() {
        // Arrange
        DynamoDBUser user1 = new DynamoDBUser();
        user1.setId("user-123");
        user1.setEmail("test1@example.com");

        DynamoDBUser user2 = new DynamoDBUser();
        user2.setId("user-123");
        user2.setEmail("test2@example.com"); // Different email

        // Act & Assert
        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void equals_DifferentId_ShouldReturnFalse() {
        // Arrange
        DynamoDBUser user1 = new DynamoDBUser();
        user1.setId("user-123");

        DynamoDBUser user2 = new DynamoDBUser();
        user2.setId("user-456");

        // Act & Assert
        assertNotEquals(user1, user2);
    }

    @Test
    void equals_NullId_ShouldCompareByReference() {
        // Arrange
        DynamoDBUser user1 = new DynamoDBUser();
        DynamoDBUser user2 = new DynamoDBUser();

        // Act & Assert
        assertEquals(user1, user1); // Same reference
        assertNotEquals(user1, user2); // Different references, both null IDs
    }

    @Test
    void equals_NullObject_ShouldReturnFalse() {
        // Arrange
        user.setId("user-123");

        // Act & Assert
        assertNotEquals(user, null);
    }

    @Test
    void equals_DifferentClass_ShouldReturnFalse() {
        // Arrange
        user.setId("user-123");
        String otherObject = "not a user";

        // Act & Assert
        assertNotEquals(user, otherObject);
    }

    @Test
    void hashCode_SameId_ShouldReturnSameHash() {
        // Arrange
        DynamoDBUser user1 = new DynamoDBUser();
        user1.setId("user-123");

        DynamoDBUser user2 = new DynamoDBUser();
        user2.setId("user-123");

        // Act & Assert
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void hashCode_DifferentId_ShouldReturnDifferentHash() {
        // Arrange
        DynamoDBUser user1 = new DynamoDBUser();
        user1.setId("user-123");

        DynamoDBUser user2 = new DynamoDBUser();
        user2.setId("user-456");

        // Act & Assert
        assertNotEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void hashCode_NullId_ShouldReturnZero() {
        // Arrange
        DynamoDBUser userWithNullId = new DynamoDBUser();

        // Act & Assert
        assertEquals(0, userWithNullId.hashCode());
    }

    @Test
    void dynamoDbAnnotations_ShouldBePresent() {
        // Test that the class has proper DynamoDB annotations
        Class<DynamoDBUser> clazz = DynamoDBUser.class;
        
        // Check @DynamoDbBean annotation
        assertTrue(clazz.isAnnotationPresent(DynamoDbBean.class));
        
        // Check that getId method has @DynamoDbPartitionKey
        try {
            Method getIdMethod = clazz.getMethod("getId");
            assertTrue(getIdMethod.isAnnotationPresent(DynamoDbPartitionKey.class));
        } catch (NoSuchMethodException e) {
            fail("getId method should exist");
        }
        
        // Check that other getter methods have @DynamoDbAttribute
        List<String> attributeMethods = Arrays.asList(
            "getEmail", "getUsername", "getFullName", "getProfileImageUrl",
            "getProvider", "getProviderId", "getPreferences", "getCreatedAt",
            "getUpdatedAt", "isActive", "isDeleted"
        );
        
        for (String methodName : attributeMethods) {
            try {
                Method method = clazz.getMethod(methodName);
                assertTrue(method.isAnnotationPresent(DynamoDbAttribute.class),
                    methodName + " should have @DynamoDbAttribute annotation");
            } catch (NoSuchMethodException e) {
                fail(methodName + " method should exist");
            }
        }
    }

    @Test
    void preferences_JsonHandling_ShouldWorkWithValidJson() {
        // Arrange
        String validJson = "{\"theme\":\"dark\",\"language\":\"en\",\"notifications\":true}";

        // Act
        user.setPreferences(validJson);

        // Assert
        assertEquals(validJson, user.getPreferences());
        
        // Verify it's actually valid JSON format (basic check)
        assertTrue(validJson.startsWith("{"));
        assertTrue(validJson.endsWith("}"));
        assertTrue(validJson.contains("\"theme\""));
    }

    @Test
    void preferences_InvalidJson_ShouldStillBeStored() {
        // Arrange
        String invalidJson = "not valid json";

        // Act
        user.setPreferences(invalidJson);

        // Assert
        assertEquals(invalidJson, user.getPreferences());
        // Note: This class doesn't validate JSON format, it just stores the string
    }

    @Test
    void booleanFields_DefaultValues_ShouldBeFalse() {
        // Arrange
        DynamoDBUser newUser = new DynamoDBUser();

        // Assert
        assertFalse(newUser.isActive());
        assertFalse(newUser.isDeleted());
    }

    @Test
    void emailValidation_ShouldAcceptValidEmails() {
        // Arrange
        List<String> validEmails = Arrays.asList(
            "test@example.com",
            "user.name+tag@domain.co.uk",
            "simple@test.org",
            "x@y.z"
        );

        // Act & Assert
        for (String email : validEmails) {
            user.setEmail(email);
            assertEquals(email, user.getEmail());
        }
    }

    @Test
    void datetimeFields_ShouldHandleNullValues() {
        // Arrange
        DynamoDBUser newUser = new DynamoDBUser();

        // Act
        newUser.setCreatedAt(null);
        newUser.setUpdatedAt(null);

        // Assert
        assertNull(newUser.getCreatedAt());
        assertNull(newUser.getUpdatedAt());
    }

    @Test
    void stringFields_ShouldHandleEmptyStrings() {
        // Act
        user.setId("");
        user.setEmail("");
        user.setUsername("");
        user.setFullName("");
        user.setProfileImageUrl("");
        user.setProvider("");
        user.setProviderId("");
        user.setPreferences("");

        // Assert
        assertEquals("", user.getId());
        assertEquals("", user.getEmail());
        assertEquals("", user.getUsername());
        assertEquals("", user.getFullName());
        assertEquals("", user.getProfileImageUrl());
        assertEquals("", user.getProvider());
        assertEquals("", user.getProviderId());
        assertEquals("", user.getPreferences());
    }

    @Test
    void fullWorkflow_CreateUpdateUser_ShouldWorkCorrectly() {
        // Arrange
        LocalDateTime createdTime = LocalDateTime.now().minusHours(1);
        LocalDateTime updatedTime = LocalDateTime.now();

        // Act - Create user
        user.setId("user-789");
        user.setEmail("workflow@example.com");
        user.setUsername("workflowuser");
        user.setFullName("Workflow Test User");
        user.setProvider("github");
        user.setProviderId("github-789");
        user.setActive(true);
        user.setCreatedAt(createdTime);
        user.setUpdatedAt(createdTime);

        // Verify initial state
        assertTrue(user.isActive());
        assertFalse(user.isDeleted());
        assertEquals(createdTime, user.getCreatedAt());
        assertEquals(createdTime, user.getUpdatedAt());

        // Act - Update user
        user.setFullName("Updated Workflow User");
        user.setUpdatedAt(updatedTime);

        // Assert final state
        assertEquals("user-789", user.getId());
        assertEquals("workflow@example.com", user.getEmail());
        assertEquals("workflowuser", user.getUsername());
        assertEquals("Updated Workflow User", user.getFullName());
        assertEquals("github", user.getProvider());
        assertEquals("github-789", user.getProviderId());
        assertTrue(user.isActive());
        assertFalse(user.isDeleted());
        assertEquals(createdTime, user.getCreatedAt());
        assertEquals(updatedTime, user.getUpdatedAt());
    }
}