package com.oddiya.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ProfileConfiguration to ensure proper profile detection and configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProfileConfigurationTest {
    
    @Autowired
    private ProfileConfiguration profileConfiguration;
    
    @Autowired
    private Environment environment;
    
    @Test
    void shouldDetectTestProfile() {
        // Given - test profile is active
        String[] activeProfiles = environment.getActiveProfiles();
        
        // When & Then
        assertThat(activeProfiles).contains("test");
        assertThat(profileConfiguration.isTestProfile()).isTrue();
        assertThat(profileConfiguration.isAwsProfile()).isFalse();
        assertThat(profileConfiguration.isJpaProfile()).isTrue();
    }
    
    @Test
    void shouldConfigureCorrectStorageType() {
        // When
        ProfileConfiguration.StorageType storageType = profileConfiguration.getStorageType();
        
        // Then
        assertThat(storageType).isEqualTo(ProfileConfiguration.StorageType.JPA);
    }
    
    @Test
    void shouldConfigureCorrectDataSourceType() {
        // When
        ProfileConfiguration.DataSourceType dataSourceType = profileConfiguration.getDataSourceType();
        
        // Then
        assertThat(dataSourceType).isEqualTo(ProfileConfiguration.DataSourceType.POSTGRESQL_TESTCONTAINERS);
    }
    
    @Test
    void shouldConfigureCorrectEnvironmentType() {
        // When
        ProfileConfiguration.EnvironmentType environmentType = profileConfiguration.getEnvironmentType();
        
        // Then
        assertThat(environmentType).isEqualTo(ProfileConfiguration.EnvironmentType.TEST);
    }
    
    @Test
    void shouldConfigureLocalStorageService() {
        // When
        ProfileConfiguration.StorageServiceType storageServiceType = profileConfiguration.getStorageServiceType();
        
        // Then
        assertThat(storageServiceType).isEqualTo(ProfileConfiguration.StorageServiceType.LOCAL);
    }
    
    @Test
    void shouldSupportJpaFeatures() {
        // When & Then
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.COMPLEX_QUERIES)).isTrue();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.TRANSACTIONS)).isTrue();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.ACID_COMPLIANCE)).isTrue();
        
        // DynamoDB features should not be supported in test profile
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.HIGH_SCALABILITY)).isFalse();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.GLOBAL_SECONDARY_INDEXES)).isFalse();
        assertThat(profileConfiguration.supportsFeature(ProfileConfiguration.Feature.EVENTUAL_CONSISTENCY)).isFalse();
    }
    
    @Test
    void shouldProvideRecommendedProfile() {
        // When & Then
        assertThat(ProfileConfiguration.getRecommendedProfile("development"))
                .isEqualTo(ProfileConfiguration.LOCAL_PROFILE);
        assertThat(ProfileConfiguration.getRecommendedProfile("testing"))
                .isEqualTo(ProfileConfiguration.TEST_PROFILE);
        assertThat(ProfileConfiguration.getRecommendedProfile("production"))
                .isEqualTo(ProfileConfiguration.AWS_PROFILE);
    }
    
    @Test
    void shouldProvideMigrationPath() {
        // When
        String migrationPath = profileConfiguration.getMigrationPath(ProfileConfiguration.AWS_PROFILE);
        
        // Then
        assertThat(migrationPath).contains("No migration needed");
    }
}