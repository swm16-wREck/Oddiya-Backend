package com.oddiya.config;

import com.oddiya.service.storage.StorageService;
import com.oddiya.service.messaging.MessagingService;
import com.oddiya.service.PlaceService;
import com.oddiya.repository.PlaceRepository;
import com.oddiya.dto.request.CreatePlaceRequest;
import com.oddiya.dto.response.PlaceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that validates the complete profile-based bean switching system.
 * This test ensures that all components work together correctly in different profiles.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileSwitchingIntegrationTest {
    
    @Autowired
    private ProfileConfiguration profileConfiguration;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private StorageService storageService;
    
    @Autowired
    private MessagingService messagingService;
    
    @Autowired
    private PlaceService placeService;
    
    @Autowired(required = false)
    private PlaceRepository placeRepository;
    
    @Autowired
    private ConditionalBeans.BeanConfigurationValidator configValidator;
    
    @Autowired
    private ConditionalBeans.ServiceLayerAdapter serviceLayerAdapter;
    
    @Test
    void shouldConfigureCorrectBeansForTestProfile() {
        // Verify profile configuration
        assertThat(profileConfiguration.isTestProfile()).isTrue();
        assertThat(profileConfiguration.getStorageType()).isEqualTo(ProfileConfiguration.StorageType.JPA);
        assertThat(profileConfiguration.getEnvironmentType()).isEqualTo(ProfileConfiguration.EnvironmentType.TEST);
    }
    
    @Test
    void shouldConfigureCorrectDataSource() {
        // Verify DataSource is H2 for test profile
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.getClass().getSimpleName()).contains("Hikari");
        
        // Verify it's H2 (indirectly by checking configuration)
        assertThat(profileConfiguration.getDataSourceType())
                .isEqualTo(ProfileConfiguration.DataSourceType.H2_MEMORY);
    }
    
    @Test
    void shouldConfigureCorrectStorageService() {
        // Verify StorageService is LocalStorageService for test profile
        assertThat(storageService).isNotNull();
        assertThat(storageService.getClass().getSimpleName()).isEqualTo("LocalStorageService");
    }
    
    @Test
    void shouldConfigureCorrectMessagingService() {
        // Verify MessagingService is LocalMessagingService for test profile
        assertThat(messagingService).isNotNull();
        assertThat(messagingService.getClass().getSimpleName()).isEqualTo("LocalMessagingService");
    }
    
    @Test
    void shouldConfigureJpaRepository() {
        // Verify JPA repository is available for test profile
        assertThat(placeRepository).isNotNull();
        assertThat(placeRepository.getClass().getSimpleName()).contains("Repository");
    }
    
    @Test
    void shouldWorkWithPlaceServiceEndToEnd() {
        // Create a test place request
        CreatePlaceRequest request = CreatePlaceRequest.builder()
                .name("Test Place")
                .description("A test place for integration testing")
                .address("123 Test Street, Test City")
                .latitude(37.7749)
                .longitude(-122.4194)
                .category("restaurant")
                .phoneNumber("+1-555-0123")
                .website("https://testplace.com")
                .openingHours("9:00 AM - 10:00 PM")
                .images(Arrays.asList("image1.jpg", "image2.jpg"))
                .tags(Arrays.asList("test", "restaurant", "casual"))
                .build();
        
        // Create the place using the service
        PlaceResponse response = placeService.createPlace(request);
        
        // Verify the response
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Test Place");
        assertThat(response.getDescription()).isEqualTo("A test place for integration testing");
        assertThat(response.getCategory()).isEqualTo("restaurant");
        assertThat(response.getLatitude()).isEqualTo(37.7749);
        assertThat(response.getLongitude()).isEqualTo(-122.4194);
        
        // Retrieve the place by ID
        PlaceResponse retrieved = placeService.getPlace(response.getId());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("Test Place");
        
        // Verify it was stored in the correct repository (JPA for test profile)
        long count = placeRepository.count();
        assertThat(count).isGreaterThan(0);
    }
    
    @Test
    void shouldValidateConfigurationCorrectly() {
        // Run configuration validation
        configValidator.validateConfiguration();
        
        // Generate configuration report
        ConditionalBeans.ConfigurationReport report = configValidator.generateConfigurationReport();
        
        assertThat(report).isNotNull();
        assertThat(report.getStorageType()).isEqualTo(ProfileConfiguration.StorageType.JPA);
        assertThat(report.getEnvironmentType()).isEqualTo(ProfileConfiguration.EnvironmentType.TEST);
        assertThat(report.getDataSourceType()).isEqualTo(ProfileConfiguration.DataSourceType.H2_MEMORY);
        assertThat(report.getStorageServiceType()).isEqualTo(ProfileConfiguration.StorageServiceType.LOCAL);
        assertThat(report.getMessagingServiceType()).isEqualTo(ProfileConfiguration.MessagingServiceType.LOCAL);
        
        // Verify supported features
        assertThat(report.getSupportedFeatures()).contains(
                ProfileConfiguration.Feature.COMPLEX_QUERIES,
                ProfileConfiguration.Feature.TRANSACTIONS,
                ProfileConfiguration.Feature.ACID_COMPLIANCE,
                ProfileConfiguration.Feature.FULL_TEXT_SEARCH
        );
        
        // Verify recommendations are provided
        assertThat(report.getRecommendations()).isNotEmpty();
    }
    
    @Test
    void shouldProvideCorrectServiceLayerAdapterBehavior() {
        // Test adapter behavior for JPA profile
        assertThat(serviceLayerAdapter.isTransactional()).isTrue();
        assertThat(serviceLayerAdapter.supportsComplexQueries()).isTrue();
        assertThat(serviceLayerAdapter.isEventuallyConsistent()).isFalse();
        
        // Test strategy recommendations
        String queryStrategy = serviceLayerAdapter.getOptimalQueryStrategy();
        assertThat(queryStrategy).contains("JOIN queries");
        assertThat(queryStrategy).contains("complex WHERE clauses");
        
        String cachingStrategy = serviceLayerAdapter.getRecommendedCachingStrategy();
        assertThat(cachingStrategy).contains("local caching");
    }
    
    @Test
    void shouldHandleFeatureSupport() {
        // Test JPA-specific features
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.COMPLEX_QUERIES)).isTrue();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.TRANSACTIONS)).isTrue();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.ACID_COMPLIANCE)).isTrue();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.FULL_TEXT_SEARCH)).isTrue();
        
        // Test DynamoDB-specific features (should be false in test profile)
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.HIGH_SCALABILITY)).isFalse();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.GLOBAL_SECONDARY_INDEXES)).isFalse();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.AUTO_SCALING)).isFalse();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.EVENTUAL_CONSISTENCY)).isFalse();
    }
}