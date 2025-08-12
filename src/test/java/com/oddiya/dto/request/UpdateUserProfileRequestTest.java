package com.oddiya.dto.request;

import com.oddiya.utils.DTOValidationTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateUserProfileRequest Validation Tests")
class UpdateUserProfileRequestTest {

    private UpdateUserProfileRequest createValidRequest() {
        return UpdateUserProfileRequest.builder()
            .name("John Doe")
            .bio("Passionate traveler and photographer")
            .profilePicture("https://example.com/profile.jpg")
            .phoneNumber("+1234567890")
            .preferredLanguage("en")
            .timezone("UTC")
            .notificationsEnabled(true)
            .isPublic(false)
            .build();
    }

    @Nested
    @DisplayName("Valid Request Tests")
    class ValidRequestTests {

        @Test
        @DisplayName("Valid complete request should pass validation")
        void validCompleteRequest() {
            UpdateUserProfileRequest request = createValidRequest();
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Valid minimal request with all nulls should pass validation")
        void validMinimalRequest() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .build();  // All fields are optional
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Request with some null fields should pass validation")
        void validWithSomeNullFields() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("John")
                .bio(null)
                .profilePicture(null)
                .phoneNumber("1234567890")
                .preferredLanguage(null)
                .timezone("EST")
                .notificationsEnabled(null)
                .isPublic(true)
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Name Validation Tests")
    class NameValidationTests {

        @Test
        @DisplayName("Null name should pass validation")
        void nullNameShouldPass() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setName(null);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Empty name should fail validation")
        void emptyNameShouldFail() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setName("");
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Name must be between 1 and 100 characters");
        }

        @Test
        @DisplayName("Blank name should fail validation")
        void blankNameShouldFail() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setName("   ");
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Name must be between 1 and 100 characters");
        }

        @Test
        @DisplayName("Name with minimum length should pass validation")
        void minimumLengthName() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setName("A");  // 1 character
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Name with maximum length should pass validation")
        void maximumLengthName() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setName("A".repeat(100));  // 100 characters
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Name exceeding maximum length should fail validation")
        void nameTooLongShouldFail() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setName("A".repeat(101));  // 101 characters
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Name must be between 1 and 100 characters");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "John Doe",
            "Mary Jane Smith",
            "Jean-Claude",
            "O'Connor",
            "李小明",
            "José María",
            "Al-Rahman",
            "Van Der Berg",
            "Dr. John Smith Jr."
        })
        @DisplayName("Valid name formats should pass validation")
        void validNameFormats(String name) {
            UpdateUserProfileRequest request = createValidRequest();
            request.setName(name);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Bio Validation Tests")
    class BioValidationTests {

        @Test
        @DisplayName("Null bio should pass validation")
        void nullBioShouldPass() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setBio(null);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Empty bio should pass validation")
        void emptyBioShouldPass() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setBio("");
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Bio with maximum length should pass validation")
        void maximumLengthBio() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setBio("A".repeat(500));  // 500 characters
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Bio exceeding maximum length should fail validation")
        void bioTooLongShouldFail() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setBio("A".repeat(501));  // 501 characters
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Bio cannot exceed 500 characters");
        }

        @Test
        @DisplayName("Bio with various content should pass validation")
        void bioWithVariousContent() {
            UpdateUserProfileRequest request = createValidRequest();
            
            String[] validBios = {
                "I love traveling!",
                "Passionate photographer 📸 and traveler 🌍",
                "Software engineer by day, adventurer by night.",
                "Born to explore the world 🗺️\nLove: hiking, photography, food\n#wanderlust",
                "Bio with \"quotes\" and 'apostrophes'",
                "Bio with emojis 🎉🌟⭐",
                "Multi-line\nbio\nwith\nbreaks",
                "Bio with numbers 123 and symbols @#$%"
            };
            
            for (String bio : validBios) {
                request.setBio(bio);
                if (bio.length() <= 500) {
                    DTOValidationTestUtils.assertValidDTO(request);
                }
            }
        }
    }

    @Nested
    @DisplayName("Optional Fields Tests")
    class OptionalFieldsTests {

        @Test
        @DisplayName("Profile picture URL should accept null")
        void profilePictureNull() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setProfilePicture(null);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Profile picture URL should accept empty string")
        void profilePictureEmpty() {
            UpdateUserProfileRequest request = createValidRequest();
            request.setProfilePicture("");
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Profile picture URL with valid URLs")
        void profilePictureValidUrls() {
            UpdateUserProfileRequest request = createValidRequest();
            
            String[] validUrls = {
                "https://example.com/image.jpg",
                "http://test.com/profile.png",
                "https://cdn.example.com/user/123/avatar.gif",
                "/static/images/default.jpg",
                "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQ...",
                "relative/path/image.png"
            };
            
            for (String url : validUrls) {
                request.setProfilePicture(url);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }

        @Test
        @DisplayName("Phone number should accept various formats")
        void phoneNumberFormats() {
            UpdateUserProfileRequest request = createValidRequest();
            
            String[] validPhoneNumbers = {
                "+1234567890",
                "123-456-7890",
                "(123) 456-7890",
                "+1 (123) 456-7890",
                "123.456.7890",
                "1234567890",
                "+44 20 7946 0958",
                "+81 3-1234-5678",
                null  // Should be valid
            };
            
            for (String phone : validPhoneNumbers) {
                request.setPhoneNumber(phone);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }

        @Test
        @DisplayName("Preferred language should accept standard codes")
        void preferredLanguageCodes() {
            UpdateUserProfileRequest request = createValidRequest();
            
            String[] validLanguageCodes = {
                "en",
                "es",
                "fr",
                "de",
                "ja",
                "zh",
                "ko",
                "pt",
                "en-US",
                "en-GB",
                "zh-CN",
                "zh-TW",
                null  // Should be valid
            };
            
            for (String lang : validLanguageCodes) {
                request.setPreferredLanguage(lang);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }

        @Test
        @DisplayName("Timezone should accept standard timezone identifiers")
        void timezoneIdentifiers() {
            UpdateUserProfileRequest request = createValidRequest();
            
            String[] validTimezones = {
                "UTC",
                "America/New_York",
                "Europe/London",
                "Asia/Tokyo",
                "Australia/Sydney",
                "Pacific/Auckland",
                "America/Los_Angeles",
                "Europe/Paris",
                "Asia/Shanghai",
                "GMT",
                "GMT+5",
                "GMT-8",
                null  // Should be valid
            };
            
            for (String timezone : validTimezones) {
                request.setTimezone(timezone);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }
    }

    @Nested
    @DisplayName("Boolean Fields Tests")
    class BooleanFieldsTests {

        @Test
        @DisplayName("Notifications enabled should accept all boolean values")
        void notificationsEnabledValues() {
            UpdateUserProfileRequest request = createValidRequest();
            
            // Test all possible Boolean values
            Boolean[] values = {true, false, null};
            
            for (Boolean value : values) {
                request.setNotificationsEnabled(value);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }

        @Test
        @DisplayName("Is public should accept all boolean values")
        void isPublicValues() {
            UpdateUserProfileRequest request = createValidRequest();
            
            // Test all possible Boolean values
            Boolean[] values = {true, false, null};
            
            for (Boolean value : values) {
                request.setIsPublic(value);
                DTOValidationTestUtils.assertValidDTO(request);
            }
        }

        @Test
        @DisplayName("Boolean fields should maintain their values")
        void booleanFieldsValues() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .notificationsEnabled(true)
                .isPublic(false)
                .build();
            
            assertEquals(Boolean.TRUE, request.getNotificationsEnabled());
            assertEquals(Boolean.FALSE, request.getIsPublic());
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Combination Validation Tests")
    class CombinationValidationTests {

        @Test
        @DisplayName("Only name too long should fail validation")
        void onlyNameTooLong() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("A".repeat(101))  // Too long
                .bio("Valid bio")       // Valid
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Name must be between 1 and 100 characters");
        }

        @Test
        @DisplayName("Only bio too long should fail validation")
        void onlyBioTooLong() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("Valid Name")     // Valid
                .bio("A".repeat(501))   // Too long
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Bio cannot exceed 500 characters");
        }

        @Test
        @DisplayName("Both name and bio too long should fail validation")
        void bothNameAndBioTooLong() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("A".repeat(101))  // Too long
                .bio("B".repeat(501))   // Too long
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 2);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Name must be between 1 and 100 characters");
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Bio cannot exceed 500 characters");
        }

        @Test
        @DisplayName("Name empty should fail even with other valid fields")
        void nameEmptyWithOtherValidFields() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("")               // Invalid
                .bio("Valid bio")       // Valid
                .phoneNumber("123456")  // Valid
                .isPublic(true)        // Valid
                .build();
            
            DTOValidationTestUtils.assertViolationCount(request, 1);
            DTOValidationTestUtils.assertHasViolationMessage(request, 
                "Name must be between 1 and 100 characters");
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("Valid complete request should serialize correctly")
        void serializationCompleteRequest() {
            UpdateUserProfileRequest request = createValidRequest();
            DTOValidationTestUtils.testSerialization(request, UpdateUserProfileRequest.class);
        }

        @Test
        @DisplayName("Request with null fields should serialize correctly")
        void serializationWithNullFields() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("John")
                .bio(null)
                .profilePicture(null)
                .phoneNumber(null)
                .preferredLanguage(null)
                .timezone(null)
                .notificationsEnabled(null)
                .isPublic(null)
                .build();
            
            DTOValidationTestUtils.testSerialization(request, UpdateUserProfileRequest.class);
        }

        @Test
        @DisplayName("Request with empty strings should serialize correctly")
        void serializationWithEmptyStrings() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("Valid Name")
                .bio("")
                .profilePicture("")
                .phoneNumber("")
                .preferredLanguage("")
                .timezone("")
                .notificationsEnabled(false)
                .isPublic(true)
                .build();
            
            DTOValidationTestUtils.testSerialization(request, UpdateUserProfileRequest.class);
        }

        @Test
        @DisplayName("Request with special characters should serialize correctly")
        void serializationWithSpecialCharacters() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("José María O'Connor")
                .bio("Love traveling 🌍 & photography 📸! \"Adventure awaits\"")
                .profilePicture("https://example.com/José's-photo.jpg")
                .phoneNumber("+1 (555) 123-4567")
                .preferredLanguage("es-MX")
                .timezone("America/Mexico_City")
                .notificationsEnabled(true)
                .isPublic(false)
                .build();
            
            DTOValidationTestUtils.testSerialization(request, UpdateUserProfileRequest.class);
        }

        @Test
        @DisplayName("Request with Unicode characters should serialize correctly")
        void serializationWithUnicodeCharacters() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("李小明")
                .bio("我喜欢旅行和摄影 🇨🇳")
                .profilePicture("https://example.com/用户头像.jpg")
                .phoneNumber("+86 138 1234 5678")
                .preferredLanguage("zh-CN")
                .timezone("Asia/Shanghai")
                .notificationsEnabled(true)
                .isPublic(false)
                .build();
            
            DTOValidationTestUtils.testSerialization(request, UpdateUserProfileRequest.class);
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Builder should create valid empty instance")
        void builderCreatesValidEmptyInstance() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .build();
            
            assertNotNull(request);
            assertNull(request.getName());
            assertNull(request.getBio());
            assertNull(request.getProfilePicture());
            assertNull(request.getPhoneNumber());
            assertNull(request.getPreferredLanguage());
            assertNull(request.getTimezone());
            assertNull(request.getNotificationsEnabled());
            assertNull(request.getIsPublic());
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Builder should handle partial field setting")
        void builderHandlesPartialFields() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("John")
                .bio("Traveler")
                .isPublic(true)
                .build();
            
            assertNotNull(request);
            assertEquals("John", request.getName());
            assertEquals("Traveler", request.getBio());
            assertEquals(Boolean.TRUE, request.getIsPublic());
            assertNull(request.getProfilePicture());
            assertNull(request.getPhoneNumber());
            assertNull(request.getPreferredLanguage());
            assertNull(request.getTimezone());
            assertNull(request.getNotificationsEnabled());
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Builder should allow method chaining")
        void builderMethodChaining() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("Jane Doe")
                .bio("Photographer")
                .profilePicture("https://example.com/jane.jpg")
                .phoneNumber("+1234567890")
                .preferredLanguage("en")
                .timezone("UTC")
                .notificationsEnabled(false)
                .isPublic(true)
                .build();
            
            assertNotNull(request);
            assertEquals("Jane Doe", request.getName());
            assertEquals("Photographer", request.getBio());
            assertEquals("https://example.com/jane.jpg", request.getProfilePicture());
            assertEquals("+1234567890", request.getPhoneNumber());
            assertEquals("en", request.getPreferredLanguage());
            assertEquals("UTC", request.getTimezone());
            assertEquals(Boolean.FALSE, request.getNotificationsEnabled());
            assertEquals(Boolean.TRUE, request.getIsPublic());
            
            DTOValidationTestUtils.assertValidDTO(request);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Boundary length values should be handled correctly")
        void boundaryLengthValues() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("A")              // Minimum valid length
                .bio("B".repeat(500))   // Maximum valid length
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("Maximum realistic field lengths should be handled")
        void maximumRealisticFieldLengths() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name("A".repeat(100))   // Maximum name length
                .bio("B".repeat(500))    // Maximum bio length
                .profilePicture("https://example.com/" + "c".repeat(200) + ".jpg")  // Long URL
                .phoneNumber("+1234567890123456")  // Long phone number
                .preferredLanguage("en-US")
                .timezone("America/Argentina/ComodRivadavia")  // Long timezone name
                .notificationsEnabled(true)
                .isPublic(false)
                .build();
            
            DTOValidationTestUtils.assertValidDTO(request);
            DTOValidationTestUtils.testSerialization(request, UpdateUserProfileRequest.class);
        }

        @Test
        @DisplayName("Whitespace handling in text fields")
        void whitespaceHandling() {
            UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .name(" John Doe ")      // Leading/trailing spaces
                .bio("  Multi  space  bio  ")  // Multiple spaces
                .profilePicture(" https://example.com/image.jpg ")
                .phoneNumber(" +1234567890 ")
                .preferredLanguage(" en ")
                .timezone(" UTC ")
                .build();
            
            // Note: Validation passes as trimming is typically handled at service layer
            DTOValidationTestUtils.assertValidDTO(request);
            
            // Values should preserve whitespace (DTO doesn't trim)
            assertEquals(" John Doe ", request.getName());
            assertEquals("  Multi  space  bio  ", request.getBio());
        }

        @Test
        @DisplayName("Very long URL should be handled")
        void veryLongUrl() {
            UpdateUserProfileRequest request = createValidRequest();
            
            // Create a very long but structurally valid URL
            String longUrl = "https://example.com/very/long/path/" + "segment/".repeat(50) + "image.jpg";
            request.setProfilePicture(longUrl);
            
            DTOValidationTestUtils.assertValidDTO(request);
        }

        @Test
        @DisplayName("International phone numbers should be handled")
        void internationalPhoneNumbers() {
            UpdateUserProfileRequest request = createValidRequest();
            
            String[] internationalNumbers = {
                "+1 (555) 123-4567",    // US
                "+44 20 7946 0958",     // UK
                "+33 1 42 86 83 26",    // France
                "+49 30 12345678",      // Germany
                "+81 3-1234-5678",      // Japan
                "+86 10 1234 5678",     // China
                "+91 11 1234 5678",     // India
                "+61 2 1234 5678",      // Australia
                "+55 11 1234-5678",     // Brazil
                "+7 495 123-45-67"      // Russia
            };
            
            for (String phoneNumber : internationalNumbers) {
                request.setPhoneNumber(phoneNumber);
                DTOValidationTestUtils.assertValidDTO(request);
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
                UpdateUserProfileRequest.class
            );
        }

        @Test
        @DisplayName("All field combinations should be handled correctly")
        void allFieldCombinations() {
            // Test with various combinations of null and non-null fields
            String[][] testCombinations = {
                {"John", null, null, null, null, null},
                {null, "Bio", null, null, null, null},
                {"John", "Bio", "http://example.com", null, null, null},
                {"John", "Bio", "http://example.com", "+1234567890", "en", "UTC"},
                {null, null, null, null, null, null}  // All null
            };
            
            for (String[] combination : testCombinations) {
                UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                    .name(combination[0])
                    .bio(combination[1])
                    .profilePicture(combination[2])
                    .phoneNumber(combination[3])
                    .preferredLanguage(combination[4])
                    .timezone(combination[5])
                    .notificationsEnabled(true)
                    .isPublic(false)
                    .build();
                
                // Should be valid unless name is empty string
                if (combination[0] != null && combination[0].trim().isEmpty()) {
                    // Will fail validation
                    DTOValidationTestUtils.assertViolationCount(request, 1);
                } else {
                    DTOValidationTestUtils.assertValidDTO(request);
                }
            }
        }
    }
}