package com.oddiya.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResourceNotFoundException Tests")
class ResourceNotFoundExceptionTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create exception with simple message")
        void shouldCreateExceptionWithSimpleMessage() {
            // Given
            String message = "Resource not found in database";
            
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
            assertNull(exception.getCause());
            assertTrue(exception instanceof RuntimeException);
        }

        @Test
        @DisplayName("Should create exception with formatted message using resource, field, and value")
        void shouldCreateExceptionWithFormattedMessage() {
            // Given
            String resource = "User";
            String field = "id";
            String value = "123";
            String expectedMessage = "User not found with id: '123'";
            
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException(resource, field, value);
            
            // Then
            assertNotNull(exception);
            assertEquals(expectedMessage, exception.getMessage());
            assertNull(exception.getCause());
        }

        @ParameterizedTest
        @CsvSource({
            "User, id, 123, 'User not found with id: '123''",
            "TravelPlan, userId, 456, 'TravelPlan not found with userId: '456''",
            "Place, name, 'Seoul Tower', 'Place not found with name: 'Seoul Tower''",
            "Review, placeId, 789, 'Review not found with placeId: '789''",
            "Video, status, ACTIVE, 'Video not found with status: 'ACTIVE''",
            "ItineraryItem, planId, 999, 'ItineraryItem not found with planId: '999''"
        })
        @DisplayName("Should format messages correctly for different resources")
        void shouldFormatMessagesCorrectlyForDifferentResources(String resource, String field, String value, String expectedMessage) {
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException(resource, field, value);
            
            // Then
            assertEquals(expectedMessage, exception.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty simple messages")
        void shouldHandleNullAndEmptySimpleMessages(String message) {
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException(message);
            
            // Then
            assertNotNull(exception);
            assertEquals(message, exception.getMessage());
        }

        @Test
        @DisplayName("Should handle null values in formatted constructor")
        void shouldHandleNullValuesInFormattedConstructor() {
            // Test with null resource
            ResourceNotFoundException exception1 = new ResourceNotFoundException(null, "id", "123");
            assertEquals("null not found with id: '123'", exception1.getMessage());

            // Test with null field
            ResourceNotFoundException exception2 = new ResourceNotFoundException("User", null, "123");
            assertEquals("User not found with null: '123'", exception2.getMessage());

            // Test with null value
            ResourceNotFoundException exception3 = new ResourceNotFoundException("User", "id", null);
            assertEquals("User not found with id: 'null'", exception3.getMessage());

            // Test with all nulls
            ResourceNotFoundException exception4 = new ResourceNotFoundException(null, null, null);
            assertEquals("null not found with null: 'null'", exception4.getMessage());
        }

        @Test
        @DisplayName("Should handle empty strings in formatted constructor")
        void shouldHandleEmptyStringsInFormattedConstructor() {
            // Test with empty resource
            ResourceNotFoundException exception1 = new ResourceNotFoundException("", "id", "123");
            assertEquals(" not found with id: '123'", exception1.getMessage());

            // Test with empty field
            ResourceNotFoundException exception2 = new ResourceNotFoundException("User", "", "123");
            assertEquals("User not found with : '123'", exception2.getMessage());

            // Test with empty value
            ResourceNotFoundException exception3 = new ResourceNotFoundException("User", "id", "");
            assertEquals("User not found with id: ''", exception3.getMessage());

            // Test with all empty strings
            ResourceNotFoundException exception4 = new ResourceNotFoundException("", "", "");
            assertEquals(" not found with : ''", exception4.getMessage());
        }

        @Test
        @DisplayName("Should handle special characters in formatted message")
        void shouldHandleSpecialCharactersInFormattedMessage() {
            // Given
            String[] specialResources = {"User&Admin", "Travel-Plan", "Place@Location"};
            String[] specialFields = {"user_id", "plan-name", "geo@coordinates"};
            String[] specialValues = {"user@domain.com", "Plan's Name", "37.5665°N"};
            
            for (int i = 0; i < specialResources.length; i++) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException(
                    specialResources[i], specialFields[i], specialValues[i]);
                
                // Then
                String expectedMessage = String.format("%s not found with %s: '%s'", 
                    specialResources[i], specialFields[i], specialValues[i]);
                assertEquals(expectedMessage, exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Message Formatting Tests")
    class MessageFormattingTests {

        @Test
        @DisplayName("Should format message with numeric values")
        void shouldFormatMessageWithNumericValues() {
            // Given
            String[] numericValues = {"123", "0", "-456", "999999999", "1.23", "0.0"};
            
            for (String numericValue : numericValues) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException("Entity", "id", numericValue);
                
                // Then
                String expectedMessage = "Entity not found with id: '" + numericValue + "'";
                assertEquals(expectedMessage, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should format message with boolean values")
        void shouldFormatMessageWithBooleanValues() {
            // Given
            String[] booleanValues = {"true", "false", "TRUE", "FALSE", "True", "False"};
            
            for (String booleanValue : booleanValues) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException("Setting", "enabled", booleanValue);
                
                // Then
                String expectedMessage = "Setting not found with enabled: '" + booleanValue + "'";
                assertEquals(expectedMessage, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should format message with UUID values")
        void shouldFormatMessageWithUUIDValues() {
            // Given
            String[] uuidValues = {
                "123e4567-e89b-12d3-a456-426614174000",
                "550e8400-e29b-41d4-a716-446655440000",
                "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
            };
            
            for (String uuid : uuidValues) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException("Resource", "uuid", uuid);
                
                // Then
                String expectedMessage = "Resource not found with uuid: '" + uuid + "'";
                assertEquals(expectedMessage, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should format message with long text values")
        void shouldFormatMessageWithLongTextValues() {
            // Given
            String longValue = "This is a very long value that might be used in search operations and contains many words and characters";
            
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException("Article", "content", longValue);
            
            // Then
            String expectedMessage = "Article not found with content: '" + longValue + "'";
            assertEquals(expectedMessage, exception.getMessage());
            assertTrue(exception.getMessage().length() > 100);
        }

        @Test
        @DisplayName("Should format message with whitespace values")
        void shouldFormatMessageWithWhitespaceValues() {
            // Given
            String[] whitespaceValues = {" ", "  ", "\t", "\n", "\r", "  \t\n  "};
            
            for (String whitespaceValue : whitespaceValues) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException("Data", "field", whitespaceValue);
                
                // Then
                String expectedMessage = "Data not found with field: '" + whitespaceValue + "'";
                assertEquals(expectedMessage, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should format message with JSON-like values")
        void shouldFormatMessageWithJsonLikeValues() {
            // Given
            String jsonValue = "{\"key\":\"value\",\"id\":123}";
            
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException("Config", "json", jsonValue);
            
            // Then
            String expectedMessage = "Config not found with json: '" + jsonValue + "'";
            assertEquals(expectedMessage, exception.getMessage());
            assertTrue(exception.getMessage().contains("{"));
            assertTrue(exception.getMessage().contains("}"));
        }
    }

    @Nested
    @DisplayName("Use Case Specific Tests")
    class UseCaseSpecificTests {

        @Test
        @DisplayName("Should handle database entity not found scenarios")
        void shouldHandleDatabaseEntityNotFoundScenarios() {
            // Given - Common database scenarios
            Object[][] scenarios = {
                {"User", "id", "123"},
                {"User", "email", "john@example.com"},
                {"TravelPlan", "id", "456"},
                {"TravelPlan", "userId", "789"},
                {"Place", "id", "999"},
                {"Place", "name", "Namsan Tower"},
                {"Review", "id", "111"},
                {"Review", "userId", "222"},
                {"ItineraryItem", "planId", "333"},
                {"SavedPlan", "userId", "444"},
                {"Video", "id", "555"},
                {"Video", "status", "PUBLISHED"}
            };
            
            for (Object[] scenario : scenarios) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException(
                    (String) scenario[0], (String) scenario[1], (String) scenario[2]);
                
                // Then
                String expectedMessage = String.format("%s not found with %s: '%s'", 
                    scenario[0], scenario[1], scenario[2]);
                assertEquals(expectedMessage, exception.getMessage());
                assertTrue(exception.getMessage().contains("not found"));
            }
        }

        @Test
        @DisplayName("Should handle composite key scenarios")
        void shouldHandleCompositeKeyScenarios() {
            // Given - Scenarios with composite identifiers
            String[] compositeValues = {
                "userId:123,planId:456",
                "lat:37.5665,lng:126.9780",
                "year:2024,month:01,day:15",
                "category:RESTAURANT,rating:5"
            };
            
            for (String compositeValue : compositeValues) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException("Entity", "compositeKey", compositeValue);
                
                // Then
                String expectedMessage = "Entity not found with compositeKey: '" + compositeValue + "'";
                assertEquals(expectedMessage, exception.getMessage());
                assertTrue(exception.getMessage().contains(":"));
                assertTrue(exception.getMessage().contains(","));
            }
        }

        @Test
        @DisplayName("Should handle enum value scenarios")
        void shouldHandleEnumValueScenarios() {
            // Given - Enum-based scenarios
            String[] enumScenarios = {
                "DRAFT", "PUBLISHED", "ARCHIVED", // TravelPlanStatus
                "PENDING", "APPROVED", "REJECTED", // Video status
                "RESTAURANT", "TOURIST_ATTRACTION", "ACCOMMODATION" // Place types
            };
            
            for (String enumValue : enumScenarios) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException("Entity", "status", enumValue);
                
                // Then
                String expectedMessage = "Entity not found with status: '" + enumValue + "'";
                assertEquals(expectedMessage, exception.getMessage());
                assertTrue(enumValue.equals(enumValue.toUpperCase())); // Verify enum convention
            }
        }

        @Test
        @DisplayName("Should handle search criteria scenarios")
        void shouldHandleSearchCriteriaScenarios() {
            // Given - Search-based scenarios
            Object[][] searchScenarios = {
                {"Place", "location", "Seoul"},
                {"TravelPlan", "destination", "Jeju Island"},
                {"Review", "rating", "5"},
                {"User", "nationality", "Korean"},
                {"Video", "duration", "300"},
                {"ItineraryItem", "day", "1"}
            };
            
            for (Object[] scenario : searchScenarios) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException(
                    (String) scenario[0], (String) scenario[1], (String) scenario[2]);
                
                // Then
                assertNotNull(exception);
                assertTrue(exception.getMessage().contains("not found with"));
                assertTrue(exception.getMessage().contains(scenario[1] + ":"));
                assertTrue(exception.getMessage().contains("'" + scenario[2] + "'"));
            }
        }
    }

    @Nested
    @DisplayName("Runtime Exception Behavior Tests")
    class RuntimeExceptionBehaviorTests {

        @Test
        @DisplayName("Should be instance of RuntimeException")
        void shouldBeInstanceOfRuntimeException() {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException("Test message");
            
            // Then
            assertTrue(exception instanceof RuntimeException);
            assertTrue(exception instanceof Exception);
            assertTrue(exception instanceof Throwable);
        }

        @Test
        @DisplayName("Should support stack trace operations")
        void shouldSupportStackTraceOperations() {
            // Given & When
            ResourceNotFoundException exception = new ResourceNotFoundException("User", "id", "123");
            
            // Then
            assertNotNull(exception.getStackTrace());
            assertTrue(exception.getStackTrace().length > 0);
            
            // Verify this method appears in stack trace
            boolean foundTestMethod = false;
            for (StackTraceElement element : exception.getStackTrace()) {
                if (element.getMethodName().equals("shouldSupportStackTraceOperations")) {
                    foundTestMethod = true;
                    break;
                }
            }
            assertTrue(foundTestMethod, "Stack trace should contain test method");
        }

        @Test
        @DisplayName("Should be throwable and catchable")
        void shouldBeThrowableAndCatchable() {
            // Test direct throwing
            assertThrows(ResourceNotFoundException.class, () -> {
                throw new ResourceNotFoundException("Direct throw test");
            });
            
            // Test formatted throwing
            assertThrows(ResourceNotFoundException.class, () -> {
                throw new ResourceNotFoundException("User", "id", "123");
            });
            
            // Test specific catching
            try {
                throw new ResourceNotFoundException("Catch test", "field", "value");
            } catch (ResourceNotFoundException e) {
                assertTrue(e.getMessage().contains("not found with field: 'value'"));
            } catch (Exception e) {
                fail("Should catch as ResourceNotFoundException");
            }
        }

        @Test
        @DisplayName("Should be catchable as RuntimeException")
        void shouldBeCatchableAsRuntimeException() {
            // Given & When & Then
            try {
                throw new ResourceNotFoundException("Runtime test", "field", "value");
            } catch (RuntimeException e) {
                assertTrue(e instanceof ResourceNotFoundException);
                assertTrue(e.getMessage().contains("not found"));
            } catch (Exception e) {
                fail("Should catch as RuntimeException");
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle very long resource names")
        void shouldHandleVeryLongResourceNames() {
            // Given
            StringBuilder longResource = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                longResource.append("Resource");
            }
            String resourceName = longResource.toString();
            
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException(resourceName, "id", "123");
            
            // Then
            assertTrue(exception.getMessage().startsWith(resourceName));
            assertTrue(exception.getMessage().contains("not found with id: '123'"));
            assertTrue(exception.getMessage().length() > 7000);
        }

        @Test
        @DisplayName("Should handle very long field names")
        void shouldHandleVeryLongFieldNames() {
            // Given
            StringBuilder longField = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                longField.append("field");
            }
            String fieldName = longField.toString();
            
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException("User", fieldName, "123");
            
            // Then
            assertTrue(exception.getMessage().contains("User not found with " + fieldName + ": '123'"));
            assertTrue(exception.getMessage().length() > 2500);
        }

        @Test
        @DisplayName("Should handle very long values")
        void shouldHandleVeryLongValues() {
            // Given
            StringBuilder longValue = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                longValue.append("X");
            }
            String value = longValue.toString();
            
            // When
            ResourceNotFoundException exception = new ResourceNotFoundException("Data", "content", value);
            
            // Then
            assertTrue(exception.getMessage().startsWith("Data not found with content: '"));
            assertTrue(exception.getMessage().endsWith("'"));
            assertTrue(exception.getMessage().contains(value));
            assertTrue(exception.getMessage().length() > 10000);
        }

        @Test
        @DisplayName("Should handle unicode characters in all parameters")
        void shouldHandleUnicodeCharactersInAllParameters() {
            // Given
            String[] unicodeResources = {"사용자", "Usuario", "Пользователь", "ユーザー"};
            String[] unicodeFields = {"이름", "nombre", "имя", "名前"};
            String[] unicodeValues = {"김철수", "José García", "Александр", "田中太郎"};
            
            for (int i = 0; i < unicodeResources.length; i++) {
                // When
                ResourceNotFoundException exception = new ResourceNotFoundException(
                    unicodeResources[i], unicodeFields[i], unicodeValues[i]);
                
                // Then
                String expectedMessage = String.format("%s not found with %s: '%s'", 
                    unicodeResources[i], unicodeFields[i], unicodeValues[i]);
                assertEquals(expectedMessage, exception.getMessage());
            }
        }

        @Test
        @DisplayName("Should handle concurrent exception creation with formatted constructor")
        void shouldHandleConcurrentExceptionCreationWithFormattedConstructor() throws InterruptedException {
            // Given
            int threadCount = 100;
            Thread[] threads = new Thread[threadCount];
            ResourceNotFoundException[] exceptions = new ResourceNotFoundException[threadCount];
            
            // When - Create exceptions concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    exceptions[index] = new ResourceNotFoundException("Resource" + index, "id", String.valueOf(index));
                });
                threads[i].start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Then
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(exceptions[i]);
                String expectedMessage = "Resource" + i + " not found with id: '" + i + "'";
                assertEquals(expectedMessage, exceptions[i].getMessage());
            }
        }

        @Test
        @DisplayName("Should handle toString with formatted message")
        void shouldHandleToStringWithFormattedMessage() {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException("User", "email", "test@example.com");
            
            // When
            String toString = exception.toString();
            
            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("ResourceNotFoundException"));
            assertTrue(toString.contains("User not found with email: 'test@example.com'"));
        }

        @Test
        @DisplayName("Should format message consistently regardless of parameter order impact")
        void shouldFormatMessageConsistentlyRegardlessOfParameterOrderImpact() {
            // Given - Test that the format is always resource, field, value
            String resource1 = "A", field1 = "B", value1 = "C";
            String resource2 = "C", field2 = "A", value2 = "B";
            
            // When
            ResourceNotFoundException exception1 = new ResourceNotFoundException(resource1, field1, value1);
            ResourceNotFoundException exception2 = new ResourceNotFoundException(resource2, field2, value2);
            
            // Then
            assertEquals("A not found with B: 'C'", exception1.getMessage());
            assertEquals("C not found with A: 'B'", exception2.getMessage());
            assertNotEquals(exception1.getMessage(), exception2.getMessage());
        }
    }
}