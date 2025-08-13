package com.oddiya.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for OAuthConfig configuration properties class.
 * Tests configuration properties binding, Google and Apple OAuth settings.
 */
@SpringBootTest
@ActiveProfiles("test")
class OAuthConfigTest {
    
    @Autowired
    private OAuthConfig oAuthConfig;
    
    @Test
    void contextLoads() {
        assertNotNull(oAuthConfig);
    }
    
    @Test
    void oAuthConfigBeanExists() {
        assertNotNull(oAuthConfig);
        assertNotNull(oAuthConfig.getGoogle());
        assertNotNull(oAuthConfig.getApple());
    }
    
    @Test
    void googleConfigHasDefaultUrls() {
        OAuthConfig.Google google = oAuthConfig.getGoogle();
        
        assertNotNull(google);
        assertEquals("https://oauth2.googleapis.com/token", google.getTokenUrl());
        assertEquals("https://www.googleapis.com/oauth2/v2/userinfo", google.getUserInfoUrl());
    }
    
    @Test
    void appleConfigHasDefaultUrls() {
        OAuthConfig.Apple apple = oAuthConfig.getApple();
        
        assertNotNull(apple);
        assertEquals("https://appleid.apple.com/auth/token", apple.getTokenUrl());
        assertEquals("https://appleid.apple.com/auth/keys", apple.getKeysUrl());
    }
    
    @Test
    void configurationPropertiesAnnotationIsPresent() {
        Class<OAuthConfig> configClass = OAuthConfig.class;
        
        boolean hasConfigurationProperties = configClass.isAnnotationPresent(
            org.springframework.boot.context.properties.ConfigurationProperties.class);
        
        assertTrue(hasConfigurationProperties, 
            "OAuthConfig should have @ConfigurationProperties annotation");
    }
    
    @Test
    void configurationAnnotationIsPresent() {
        Class<OAuthConfig> configClass = OAuthConfig.class;
        
        boolean hasConfiguration = configClass.isAnnotationPresent(
            org.springframework.context.annotation.Configuration.class);
        
        assertTrue(hasConfiguration, 
            "OAuthConfig should have @Configuration annotation");
    }
    
    @Test
    void configurationPropertiesPrefixIsCorrect() {
        org.springframework.boot.context.properties.ConfigurationProperties annotation = 
            OAuthConfig.class.getAnnotation(
                org.springframework.boot.context.properties.ConfigurationProperties.class);
        
        assertNotNull(annotation);
        assertEquals("oauth", annotation.prefix());
    }
    
    /**
     * Test OAuthConfig with Google properties configured
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "oauth.google.client-id=test-google-client-id",
        "oauth.google.client-secret=test-google-client-secret",
        "oauth.google.redirect-uri=https://example.com/oauth/google/callback",
        "oauth.google.token-url=https://custom.googleapis.com/token",
        "oauth.google.user-info-url=https://custom.googleapis.com/userinfo"
    })
    @ActiveProfiles("test")
    static class GoogleOAuthPropertiesTest {
        
        @Autowired
        private OAuthConfig oAuthConfig;
        
        @Test
        void googlePropertiesAreBoundCorrectly() {
            OAuthConfig.Google google = oAuthConfig.getGoogle();
            
            assertNotNull(google);
            assertEquals("test-google-client-id", google.getClientId());
            assertEquals("test-google-client-secret", google.getClientSecret());
            assertEquals("https://example.com/oauth/google/callback", google.getRedirectUri());
            assertEquals("https://custom.googleapis.com/token", google.getTokenUrl());
            assertEquals("https://custom.googleapis.com/userinfo", google.getUserInfoUrl());
        }
    }
    
    /**
     * Test OAuthConfig with Apple properties configured
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "oauth.apple.client-id=test-apple-client-id",
        "oauth.apple.team-id=test-team-id",
        "oauth.apple.key-id=test-key-id",
        "oauth.apple.private-key=test-private-key",
        "oauth.apple.redirect-uri=https://example.com/oauth/apple/callback",
        "oauth.apple.token-url=https://custom.appleid.apple.com/token",
        "oauth.apple.keys-url=https://custom.appleid.apple.com/keys"
    })
    @ActiveProfiles("test")
    static class AppleOAuthPropertiesTest {
        
        @Autowired
        private OAuthConfig oAuthConfig;
        
        @Test
        void applePropertiesAreBoundCorrectly() {
            OAuthConfig.Apple apple = oAuthConfig.getApple();
            
            assertNotNull(apple);
            assertEquals("test-apple-client-id", apple.getClientId());
            assertEquals("test-team-id", apple.getTeamId());
            assertEquals("test-key-id", apple.getKeyId());
            assertEquals("test-private-key", apple.getPrivateKey());
            assertEquals("https://example.com/oauth/apple/callback", apple.getRedirectUri());
            assertEquals("https://custom.appleid.apple.com/token", apple.getTokenUrl());
            assertEquals("https://custom.appleid.apple.com/keys", apple.getKeysUrl());
        }
    }
    
    @Test
    void googleInnerClassExists() {
        OAuthConfig.Google google = new OAuthConfig.Google();
        assertNotNull(google);
        
        // Test getters and setters
        google.setClientId("test-id");
        assertEquals("test-id", google.getClientId());
        
        google.setClientSecret("test-secret");
        assertEquals("test-secret", google.getClientSecret());
        
        google.setRedirectUri("https://test.com/callback");
        assertEquals("https://test.com/callback", google.getRedirectUri());
    }
    
    @Test
    void appleInnerClassExists() {
        OAuthConfig.Apple apple = new OAuthConfig.Apple();
        assertNotNull(apple);
        
        // Test getters and setters
        apple.setClientId("test-id");
        assertEquals("test-id", apple.getClientId());
        
        apple.setTeamId("test-team");
        assertEquals("test-team", apple.getTeamId());
        
        apple.setKeyId("test-key");
        assertEquals("test-key", apple.getKeyId());
        
        apple.setPrivateKey("test-private-key");
        assertEquals("test-private-key", apple.getPrivateKey());
        
        apple.setRedirectUri("https://test.com/callback");
        assertEquals("https://test.com/callback", apple.getRedirectUri());
    }
    
    @Test
    void dataAnnotationIsPresent() {
        // Verify @Data annotation is present on inner classes
        Class<OAuthConfig.Google> googleClass = OAuthConfig.Google.class;
        Class<OAuthConfig.Apple> appleClass = OAuthConfig.Apple.class;
        
        // Check for Lombok annotation by name since lombok may not be on test classpath
        boolean googleHasData = false;
        boolean appleHasData = false;
        
        for (java.lang.annotation.Annotation annotation : googleClass.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("Data")) {
                googleHasData = true;
                break;
            }
        }
        
        for (java.lang.annotation.Annotation annotation : appleClass.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("Data")) {
                appleHasData = true;
                break;
            }
        }
        
        // Since Lombok generates methods at compile time, we can also check for generated methods
        // as an alternative verification that @Data is working
        if (!googleHasData) {
            try {
                googleClass.getMethod("getClientId");
                googleClass.getMethod("setClientId", String.class);
                googleHasData = true; // If getter/setter exist, @Data annotation worked
            } catch (NoSuchMethodException e) {
                // Method not found, annotation might not be working
            }
        }
        
        if (!appleHasData) {
            try {
                appleClass.getMethod("getClientId");
                appleClass.getMethod("setClientId", String.class);
                appleHasData = true; // If getter/setter exist, @Data annotation worked
            } catch (NoSuchMethodException e) {
                // Method not found, annotation might not be working
            }
        }
        
        assertTrue(googleHasData, "Google inner class should have @Data annotation or generated methods");
        assertTrue(appleHasData, "Apple inner class should have @Data annotation or generated methods");
    }
    
    @Test
    void defaultValuesAreCorrect() {
        // Create new instances to test default values
        OAuthConfig.Google google = new OAuthConfig.Google();
        OAuthConfig.Apple apple = new OAuthConfig.Apple();
        
        // Google defaults
        assertEquals("https://oauth2.googleapis.com/token", google.getTokenUrl());
        assertEquals("https://www.googleapis.com/oauth2/v2/userinfo", google.getUserInfoUrl());
        
        // Apple defaults
        assertEquals("https://appleid.apple.com/auth/token", apple.getTokenUrl());
        assertEquals("https://appleid.apple.com/auth/keys", apple.getKeysUrl());
    }
    
    @Test
    void toStringMethodsWork() {
        // Test Lombok-generated toString methods
        OAuthConfig.Google google = oAuthConfig.getGoogle();
        OAuthConfig.Apple apple = oAuthConfig.getApple();
        
        String googleString = google.toString();
        String appleString = apple.toString();
        
        assertNotNull(googleString);
        assertNotNull(appleString);
        assertTrue(googleString.contains("Google"));
        assertTrue(appleString.contains("Apple"));
    }
    
    @Test
    void equalsAndHashCodeMethodsWork() {
        // Test Lombok-generated equals and hashCode methods
        OAuthConfig.Google google1 = new OAuthConfig.Google();
        OAuthConfig.Google google2 = new OAuthConfig.Google();
        
        google1.setClientId("same-id");
        google2.setClientId("same-id");
        
        assertEquals(google1, google2);
        assertEquals(google1.hashCode(), google2.hashCode());
        
        google2.setClientId("different-id");
        assertNotEquals(google1, google2);
    }
}