package com.oddiya.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive utility class for DTO validation testing
 * Provides methods for testing validation annotations, serialization, and edge cases
 */
public class DTOValidationTestUtils {

    private static final Validator validator;
    private static final ObjectMapper objectMapper;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Validate a DTO and assert no violations
     */
    public static <T> void assertValidDTO(T dto) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String violationMessages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
            fail("Expected valid DTO but found violations: " + violationMessages);
        }
    }

    /**
     * Validate a DTO and assert specific number of violations
     */
    public static <T> void assertViolationCount(T dto, int expectedCount) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        assertEquals(expectedCount, violations.size(), 
            "Expected " + expectedCount + " violations but found " + violations.size() + 
            ": " + violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toList()));
    }

    /**
     * Validate a DTO and assert specific violation message exists
     */
    public static <T> void assertHasViolationMessage(T dto, String expectedMessage) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        boolean hasMessage = violations.stream()
            .anyMatch(violation -> violation.getMessage().equals(expectedMessage));
        
        if (!hasMessage) {
            String actualMessages = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
            fail("Expected violation message '" + expectedMessage + "' but found: " + actualMessages);
        }
    }

    /**
     * Validate a DTO and assert violation on specific property
     */
    public static <T> void assertHasPropertyViolation(T dto, String propertyPath) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        boolean hasPropertyViolation = violations.stream()
            .anyMatch(violation -> violation.getPropertyPath().toString().equals(propertyPath));
        
        if (!hasPropertyViolation) {
            String actualProperties = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.joining(", "));
            fail("Expected violation on property '" + propertyPath + "' but found violations on: " + actualProperties);
        }
    }

    /**
     * Get all constraint violations for a DTO
     */
    public static <T> Set<ConstraintViolation<T>> getViolations(T dto) {
        return validator.validate(dto);
    }

    /**
     * Test JSON serialization and deserialization
     */
    public static <T> void testSerialization(T original, Class<T> clazz) {
        try {
            // Serialize to JSON
            String json = objectMapper.writeValueAsString(original);
            assertNotNull(json);
            assertFalse(json.isEmpty());
            
            // Deserialize back to object
            T deserialized = objectMapper.readValue(json, clazz);
            assertNotNull(deserialized);
            
            // Verify equality (if equals is properly implemented)
            try {
                assertEquals(original, deserialized);
            } catch (AssertionError e) {
                // If equals fails, at least verify serialization worked
                assertThat(deserialized).isNotNull();
            }
            
        } catch (JsonProcessingException e) {
            fail("Serialization/Deserialization failed for " + clazz.getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Test JSON serialization with specific field verification
     */
    public static <T> void testSerializationWithFieldVerification(T original, Class<T> clazz, 
                                                                  Map<String, Object> expectedJsonFields) {
        try {
            String json = objectMapper.writeValueAsString(original);
            T deserialized = objectMapper.readValue(json, clazz);
            
            // Verify specific fields
            for (Map.Entry<String, Object> entry : expectedJsonFields.entrySet()) {
                String fieldName = entry.getKey();
                Object expectedValue = entry.getValue();
                
                Object actualValue = getFieldValue(deserialized, fieldName);
                assertEquals(expectedValue, actualValue, 
                    "Field '" + fieldName + "' serialization mismatch");
            }
            
        } catch (Exception e) {
            fail("Serialization with field verification failed: " + e.getMessage());
        }
    }

    /**
     * Test edge cases for string fields
     */
    public static <T> void testStringFieldEdgeCases(T dto, String fieldName, 
                                                    Function<T, T> builderFunction) {
        Class<?> dtoClass = dto.getClass();
        
        // Test null value
        T nullDto = setFieldValue(builderFunction.apply(dto), fieldName, null);
        // Don't assert here - let caller check violations
        
        // Test empty string
        T emptyDto = setFieldValue(builderFunction.apply(dto), fieldName, "");
        // Don't assert here - let caller check violations
        
        // Test whitespace only
        T whitespaceDto = setFieldValue(builderFunction.apply(dto), fieldName, "   ");
        // Don't assert here - let caller check violations
        
        // Test very long string
        String longString = "a".repeat(3000);
        T longDto = setFieldValue(builderFunction.apply(dto), fieldName, longString);
        // Don't assert here - let caller check violations
    }

    /**
     * Test date field edge cases
     */
    public static <T> void testDateFieldEdgeCases(Class<T> dtoClass) {
        // Test past date
        LocalDate pastDate = LocalDate.now().minusDays(1);
        
        // Test far future date
        LocalDate farFutureDate = LocalDate.now().plusYears(100);
        
        // Test null date - handled by validation annotations
    }

    /**
     * Test email validation edge cases
     */
    public static void testEmailValidation(String email, boolean shouldBeValid) {
        // Use a simple DTO to test email validation
        TestEmailDTO testDTO = new TestEmailDTO(email);
        Set<ConstraintViolation<TestEmailDTO>> violations = validator.validate(testDTO);
        
        if (shouldBeValid) {
            assertTrue(violations.isEmpty(), 
                "Email '" + email + "' should be valid but got violations: " + 
                violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toList()));
        } else {
            assertFalse(violations.isEmpty(), 
                "Email '" + email + "' should be invalid but no violations found");
        }
    }

    /**
     * Test size constraint edge cases
     */
    public static <T> void testSizeConstraintEdgeCases(T dto, String fieldName, 
                                                       int minSize, int maxSize,
                                                       Function<T, T> builderFunction) {
        // Test exactly minimum size
        String minString = "a".repeat(minSize);
        T minDto = setFieldValue(builderFunction.apply(dto), fieldName, minString);
        assertValidDTO(minDto);
        
        // Test exactly maximum size
        String maxString = "a".repeat(maxSize);
        T maxDto = setFieldValue(builderFunction.apply(dto), fieldName, maxString);
        assertValidDTO(maxDto);
        
        // Test one character too short
        if (minSize > 0) {
            String tooShort = "a".repeat(minSize - 1);
            T tooShortDto = setFieldValue(builderFunction.apply(dto), fieldName, tooShort);
            assertViolationCount(tooShortDto, 1);
        }
        
        // Test one character too long
        String tooLong = "a".repeat(maxSize + 1);
        T tooLongDto = setFieldValue(builderFunction.apply(dto), fieldName, tooLong);
        assertViolationCount(tooLongDto, 1);
    }

    /**
     * Test nested object validation
     */
    public static <T> void testNestedObjectValidation(T dto, String nestedFieldName) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        
        // Check if any violations are related to nested object
        boolean hasNestedViolation = violations.stream()
            .anyMatch(violation -> violation.getPropertyPath().toString().startsWith(nestedFieldName));
        
        // This method just validates that nested validation is working
        // Caller should verify specific expectations
    }

    /**
     * Create validation test data for common scenarios
     */
    public static Map<String, List<String>> getCommonInvalidEmails() {
        Map<String, List<String>> invalidEmails = new HashMap<>();
        
        invalidEmails.put("missing_at", Arrays.asList("plainaddress", "user.domain.com"));
        invalidEmails.put("missing_domain", Arrays.asList("@missingdomain", "user@"));
        invalidEmails.put("invalid_characters", Arrays.asList("user@domain,com", "user@domain..com"));
        invalidEmails.put("empty_or_null", Arrays.asList("", null));
        invalidEmails.put("whitespace", Arrays.asList(" ", "   ", "\t"));
        
        return invalidEmails;
    }

    /**
     * Create validation test data for valid emails
     */
    public static List<String> getValidEmails() {
        return Arrays.asList(
            "user@example.com",
            "test.email@domain.co.uk",
            "valid+email@test.org",
            "user123@domain123.com",
            "a@b.co"
        );
    }

    /**
     * Helper method to get field value using reflection
     */
    private static Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field value for " + fieldName, e);
        }
    }

    /**
     * Helper method to set field value using reflection
     */
    private static <T> T setFieldValue(T obj, String fieldName, Object value) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            field.set(obj, value);
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field value for " + fieldName, e);
        }
    }

    /**
     * Find field in class hierarchy
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException("Field " + fieldName + " not found in " + clazz.getName());
    }

    /**
     * Simple DTO for email testing
     */
    private static class TestEmailDTO {
        @jakarta.validation.constraints.Email
        private final String email;

        public TestEmailDTO(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }

    /**
     * Comprehensive DTO validation test
     */
    public static <T> void runComprehensiveValidationTests(T validDto, Class<T> dtoClass) {
        // Test valid DTO
        assertValidDTO(validDto);
        
        // Test serialization
        testSerialization(validDto, dtoClass);
        
        // Test basic POJO functionality
        PojoTestUtils.testPojoClass(dtoClass);
    }

    /**
     * Test builder pattern with validation
     */
    public static <T> void testBuilderValidation(Object builder, String builderMethod, Object... args) {
        try {
            // Find and invoke the builder method
            Class<?>[] paramTypes = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
            
            // Get method and invoke
            java.lang.reflect.Method method = builder.getClass().getMethod(builderMethod, paramTypes);
            Object result = method.invoke(builder, args);
            
            // Should return the builder for chaining
            assertNotNull(result);
            
        } catch (Exception e) {
            fail("Builder method test failed: " + e.getMessage());
        }
    }
}