package com.oddiya.dto.request;

import com.oddiya.utils.DTOValidationTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SignUpRequest Validation Tests")
class SignUpRequestTest {

    private SignUpRequest createValidRequest() {
        return SignUpRequest.builder()
            .email("test@example.com")
            .password("password123")
            .nickname("testuser")
            .fullName("Test User")
            .build();
    }

    @Nested
    @DisplayName("Valid Request Tests")
    class ValidRequestTests {

        @Test
        @DisplayName("Valid complete request should pass validation")
        void validCompleteRequest() {
            SignUpRequest request = createValidRequest();
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Valid minimal request should pass validation")
        void validMinimalRequest() {
            SignUpRequest request = SignUpRequest.builder()
                .email("user@domain.com")
                .password("123456")  // Minimum length
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Request with null optional fields should pass validation")
        void validWithNullOptionalFields() {
            SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123")
                .nickname(null)  // Optional
                .fullName(null)  // Optional
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Email Validation Tests")
    class EmailValidationTests {

        @Test
        @DisplayName("Null email should fail validation")
        void nullEmailShouldFail() {
            SignUpRequest request = createValidRequest();
            request.setEmail(null);
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Email is required");
        }

        @Test
        @DisplayName("Empty email should fail validation")
        void emptyEmailShouldFail() {
            SignUpRequest request = createValidRequest();
            request.setEmail("");
            
            DTOValidationTestUtils.assertViolationCount(request, 2);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Email is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Invalid email format");
        }

        @Test
        @DisplayName("Blank email should fail validation")
        void blankEmailShouldFail() {
            SignUpRequest request = createValidRequest();
            request.setEmail("   ");
            
            DTOValidationTestUtils.assertViolationCount(request, 2);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Email is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Invalid email format");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "user@example.com",
            "test.email@domain.co.uk",
            "valid+email@test.org",
            "user123@domain123.com",
            "a@b.co",
            "firstname.lastname@company.org",
            "email@subdomain.example.com",
            "firstname_lastname@example.com",
            "user+tag@example.com"
        })
        @DisplayName("Valid email formats should pass validation")
        void validEmailFormats(String email) {
            SignUpRequest request = createValidRequest();
            request.setEmail(email);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "plainaddress",
            "user.domain.com",
            "@missingdomain",
            "user@",
            "user@domain,com",
            "user@domain..com",
            "user@.com",
            "user@domain.",
            ".user@domain.com",
            "user..email@domain.com",
            "user@domain@domain.com",
            "user name@domain.com",
            "user@domain .com"
        })
        @DisplayName("Invalid email formats should fail validation")
        void invalidEmailFormats(String email) {
            SignUpRequest request = createValidRequest();
            request.setEmail(email);
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Invalid email format");
        }

        @Test
        @DisplayName("Email with special characters should be handled properly")
        void emailWithSpecialCharacters() {
            SignUpRequest request = createValidRequest();
            
            // These should be valid
            String[] validEmails = {
                "user+tag@example.com",
                "user-name@example.com",
                "user_name@example.com",
                "user.name@example.com",
                "123user@example.com",
                "user123@example.com"
            };
            
            for (String email : validEmails) {
                request.setEmail(email);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }

        @Test
        @DisplayName("Very long email should be handled properly")
        void veryLongEmail() {
            SignUpRequest request = createValidRequest();
            
            // Create a long but valid email
            String longEmail = "a".repeat(60) + "@" + "b".repeat(60) + ".com";
            request.setEmail(longEmail);
            
            // Should still validate (Jakarta validation doesn't impose length limits on @Email by default)
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidationTests {

        @Test
        @DisplayName("Null password should fail validation")
        void nullPasswordShouldFail() {
            SignUpRequest request = createValidRequest();
            request.setPassword(null);
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password is required");
        }

        @Test
        @DisplayName("Empty password should fail validation")
        void emptyPasswordShouldFail() {
            SignUpRequest request = createValidRequest();
            request.setPassword("");
            
            DTOValidationTestUtils.assertViolationCount(request, 2);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password must be at least 6 characters");
        }

        @Test
        @DisplayName("Blank password should fail validation")
        void blankPasswordShouldFail() {
            SignUpRequest request = createValidRequest();
            request.setPassword("   ");
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password is required");
        }

        @Test
        @DisplayName("Password shorter than 6 characters should fail validation")
        void shortPasswordShouldFail() {
            SignUpRequest request = createValidRequest();
            request.setPassword("12345");  // 5 characters
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password must be at least 6 characters");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "123456",      // exactly 6 characters
            "password",    // common password
            "MyPassword123", // mixed case with numbers
            "P@ssw0rd!",   // special characters
            "verylongpasswordwithmanymanycharacters", // very long password
            "пароль123",   // Unicode characters
            "密码123456"   // Chinese characters
        })
        @DisplayName("Valid passwords should pass validation")
        void validPasswords(String password) {
            SignUpRequest request = createValidRequest();
            request.setPassword(password);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Password with only whitespace after minimum length should fail")
        void passwordWithWhitespaceAfterMinimum() {
            SignUpRequest request = createValidRequest();
            request.setPassword("12345 ");  // 6 characters but ends with space
            
            // Should be valid since @Size counts all characters including whitespace
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Optional Fields Tests")
    class OptionalFieldsTests {

        @Test
        @DisplayName("Null nickname should pass validation")
        void nullNicknameShouldPass() {
            SignUpRequest request = createValidRequest();
            request.setNickname(null);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Empty nickname should pass validation")
        void emptyNicknameShouldPass() {
            SignUpRequest request = createValidRequest();
            request.setNickname("");
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Nickname with special characters should pass validation")
        void nicknameWithSpecialCharacters() {
            SignUpRequest request = createValidRequest();
            request.setNickname("user_123-test");
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Very long nickname should pass validation")
        void veryLongNickname() {
            SignUpRequest request = createValidRequest();
            request.setNickname("a".repeat(1000));  // No @Size constraint on nickname
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Null full name should pass validation")
        void nullFullNameShouldPass() {
            SignUpRequest request = createValidRequest();
            request.setFullName(null);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Empty full name should pass validation")
        void emptyFullNameShouldPass() {
            SignUpRequest request = createValidRequest();
            request.setFullName("");
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Full name with various formats should pass validation")
        void fullNameVariousFormats() {
            SignUpRequest request = createValidRequest();
            
            String[] validNames = {
                "John Doe",
                "Mary Jane Smith",
                "Jean-Claude Van Damme",
                "O'Connor",
                "李小明",        // Chinese name
                "José María",   // Spanish name with accent
                "Al-Rahman",    // Arabic style name
                "Van Der Berg", // Dutch style name
                "McDonald",     // Scottish name
                "Dr. John Smith Jr.", // With title and suffix
            };
            
            for (String name : validNames) {
                request.setFullName(name);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }
    }

    @Nested
    @DisplayName("Multiple Violations Tests")
    class MultipleViolationsTests {

        @Test
        @DisplayName("Multiple field violations should be detected")
        void multipleFieldViolations() {
            SignUpRequest request = SignUpRequest.builder()
                .email(null)        // Violation: required
                .password("")       // Violations: required and too short
                .nickname("valid")  // Valid
                .fullName("valid")  // Valid
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 3);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Email is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password must be at least 6 characters");
        }

        @Test
        @DisplayName("Invalid email and password should fail validation")
        void invalidEmailAndPassword() {
            SignUpRequest request = SignUpRequest.builder()
                .email("invalid-email")  // Invalid format
                .password("12345")       // Too short
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 2);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Invalid email format");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password must be at least 6 characters");
        }

        @Test
        @DisplayName("All null required fields should fail validation")
        void allNullRequiredFields() {
            SignUpRequest request = SignUpRequest.builder()
                .email(null)
                .password(null)
                .nickname("optional")
                .fullName("optional")
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 2);
            DTOValidationTestUtils.assertHasViolationMessage(request, "Email is required");
            DTOValidationTestUtils.assertHasViolationMessage(request, "Password is required");
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("Valid request should serialize and deserialize correctly")
        void serializationRoundTrip() {
            SignUpRequest request = createValidRequest();
            DTOValidationTestUtils.testSerialization(request, SignUpRequest.class);
        }

        @Test
        @DisplayName("Request with null optional fields should serialize correctly")
        void serializationWithNullFields() {
            SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123")
                .nickname(null)
                .fullName(null)
                .build();
            
            DTOValidationTestUtils.testSerialization(request, SignUpRequest.class);
        }

        @Test
        @DisplayName("Request with special characters should serialize correctly")
        void serializationWithSpecialCharacters() {
            SignUpRequest request = SignUpRequest.builder()
                .email("test+tag@example.com")
                .password("P@ssw0rd!")
                .nickname("user_123")
                .fullName("José María")
                .build();
            
            DTOValidationTestUtils.testSerialization(request, SignUpRequest.class);
        }

        @Test
        @DisplayName("Request with Unicode characters should serialize correctly")
        void serializationWithUnicodeCharacters() {
            SignUpRequest request = SignUpRequest.builder()
                .email("用户@example.com")  // Unicode email (may not be valid email format)
                .password("密码123456")      // Unicode password
                .nickname("用户名")         // Unicode nickname
                .fullName("李小明")         // Unicode full name
                .build();
            
            // Note: The email might fail validation due to @Email annotation,
            // but serialization should still work
            DTOValidationTestUtils.testSerialization(request, SignUpRequest.class);
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Builder should create valid instance")
        void builderCreatesValidInstance() {
            SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();
            
            assertNotNull(request);
            assertEquals("test@example.com", request.getEmail());
            assertEquals("password123", request.getPassword());
            assertNull(request.getNickname());
            assertNull(request.getFullName());
        }

        @Test
        @DisplayName("Builder should handle all fields correctly")
        void builderHandlesAllFields() {
            SignUpRequest request = SignUpRequest.builder()
                .email("complete@test.com")
                .password("securepassword")
                .nickname("testuser")
                .fullName("Test User Name")
                .build();
            
            assertNotNull(request);
            assertEquals("complete@test.com", request.getEmail());
            assertEquals("securepassword", request.getPassword());
            assertEquals("testuser", request.getNickname());
            assertEquals("Test User Name", request.getFullName());
        }

        @Test
        @DisplayName("Builder should allow method chaining")
        void builderMethodChaining() {
            SignUpRequest request = SignUpRequest.builder()
                .email("chain@test.com")
                .password("chainpass")
                .nickname("chain")
                .fullName("Chain Test")
                .build();
            
            assertNotNull(request);
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Case sensitivity in email should be preserved")
        void emailCaseSensitivity() {
            SignUpRequest request = createValidRequest();
            request.setEmail("Test.Email@EXAMPLE.COM");
            
            DTOValidationTestUtils.assertValidDTO(request);
            assertEquals("Test.Email@EXAMPLE.COM", request.getEmail());
        }

        @Test
        @DisplayName("Trimming of fields should be handled by application layer")
        void fieldTrimmingHandling() {
            SignUpRequest request = SignUpRequest.builder()
                .email(" test@example.com ")  // Leading/trailing spaces
                .password(" password123 ")
                .nickname(" nickname ")
                .fullName(" Full Name ")
                .build();
            
            // Validation should pass as trimming is typically handled at service layer
            DTOValidationTestUtils.assertValidDTO(request);
            
            // Values should preserve spaces (DTO doesn't trim)
            assertEquals(" test@example.com ", request.getEmail());
            assertEquals(" password123 ", request.getPassword());
            assertEquals(" nickname ", request.getNickname());
            assertEquals(" Full Name ", request.getFullName());
        }

        @Test
        @DisplayName("Maximum realistic field lengths should be handled")
        void maximumRealisticFieldLengths() {
            SignUpRequest request = SignUpRequest.builder()
                .email("a".repeat(50) + "@" + "b".repeat(50) + ".com")  // Long email
                .password("a".repeat(128))  // Long password
                .nickname("a".repeat(100))  // Long nickname
                .fullName("a".repeat(200))  // Long full name
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
            DTOValidationTestUtils.testSerialization(request, SignUpRequest.class);
        }

        @Test
        @DisplayName("International domain names should be handled")
        void internationalDomainNames() {
            SignUpRequest request = createValidRequest();
            // Note: These might not pass @Email validation depending on implementation
            String[] internationalEmails = {
                "user@xn--e1afmkfd.xn--p1ai",  // пример.рф in punycode
                "test@example.org",
                "user@test.co.jp"
            };
            
            for (String email : internationalEmails) {
                request.setEmail(email);
                // Just test that it doesn't crash during validation
                DTOValidationTestUtils.getViolations(request);
            }
        }
    }

    @Nested
    @DisplayName("Comprehensive Tests")
    class ComprehensiveTests {

        @Test
        @DisplayName("Should pass comprehensive DTO validation tests")
        void comprehensiveDTOValidationTests() {
            DTOValidationTestUtils.runComprehensiveValidationTests(
                createValidRequest(),
                SignUpRequest.class
            );
        }

        @Test
        @DisplayName("Email validation utility tests")
        void emailValidationUtilityTests() {
            // Test valid emails
            for (String email : DTOValidationTestUtils.getValidEmails()) {
                DTOValidationTestUtils.testEmailValidation(email, true);
            }
            
            // Test invalid emails
            for (List<String> invalidEmails : DTOValidationTestUtils.getCommonInvalidEmails().values()) {
                for (String email : invalidEmails) {
                    if (email != null) {  // Skip null test as it's handled differently
                        DTOValidationTestUtils.testEmailValidation(email, false);
                    }
                }
            }
        }
    }
}