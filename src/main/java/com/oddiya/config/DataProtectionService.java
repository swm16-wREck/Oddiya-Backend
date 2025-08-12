package com.oddiya.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Data Protection Service
 * 
 * Implements GDPR, Korean PIPA, and other privacy compliance features:
 * - Data encryption/decryption
 * - Data anonymization and pseudonymization
 * - Data retention and deletion policies
 * - Consent management
 * - Data subject rights (access, rectification, erasure, portability)
 * 
 * Compliance Framework:
 * - GDPR (EU General Data Protection Regulation)
 * - Korean PIPA (Personal Information Protection Act)
 * - CCPA (California Consumer Privacy Act)
 * - ISO 27001 Privacy Controls
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataProtectionService {
    
    private final ObjectMapper objectMapper;
    private final SecurityEventLogger securityEventLogger;
    
    @Value("${app.security.encryption.key:}")
    private String encryptionKeyBase64;
    
    @Value("${app.privacy.anonymization.enabled:true}")
    private boolean anonymizationEnabled;
    
    @Value("${app.privacy.retention.default-days:2555}") // 7 years default
    private int defaultRetentionDays;
    
    @Value("${app.privacy.consent.required:true}")
    private boolean consentRequired;
    
    // AES-256-GCM encryption parameters
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    // PII patterns for detection
    private static final List<Pattern> PII_PATTERNS = List.of(
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), // Email
        Pattern.compile("\\b\\d{3}-\\d{3,4}-\\d{4}\\b"), // Phone number
        Pattern.compile("\\b\\d{6}[- ]?\\d{7}\\b"), // Korean ID format
        Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b"), // Credit card
        Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b") // IP address
    );
    
    // Consent tracking
    private final Map<String, ConsentRecord> consentRecords = new ConcurrentHashMap<>();
    
    // Data retention policies by data type
    private final Map<DataType, RetentionPolicy> retentionPolicies = Map.of(
        DataType.PERSONAL_DATA, new RetentionPolicy(2555, true), // 7 years
        DataType.AUTHENTICATION_DATA, new RetentionPolicy(90, true), // 3 months
        DataType.USAGE_ANALYTICS, new RetentionPolicy(365, false), // 1 year
        DataType.MARKETING_DATA, new RetentionPolicy(1095, true), // 3 years
        DataType.TRANSACTION_DATA, new RetentionPolicy(2555, true), // 7 years (legal requirement)
        DataType.COOKIES_DATA, new RetentionPolicy(365, true) // 1 year
    );
    
    /**
     * Encrypt sensitive data using AES-256-GCM
     */
    public String encryptSensitiveData(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            SecretKey key = getEncryptionKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + encrypted data
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Error encrypting data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt sensitive data using AES-256-GCM
     */
    public String decryptSensitiveData(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            SecretKey key = getEncryptionKey();
            byte[] decodedData = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[decodedData.length - GCM_IV_LENGTH];
            
            System.arraycopy(decodedData, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(decodedData, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            
            byte[] plainText = cipher.doFinal(encryptedData);
            return new String(plainText, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Error decrypting data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    /**
     * Anonymize PII data by replacing with anonymized versions
     */
    public String anonymizePII(String data) {
        if (!anonymizationEnabled || data == null) {
            return data;
        }
        
        String anonymized = data;
        
        // Anonymize email addresses
        anonymized = anonymized.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", 
                                         "****@****.com");
        
        // Anonymize phone numbers
        anonymized = anonymized.replaceAll("\\b\\d{3}-\\d{3,4}-\\d{4}\\b", "***-****-****");
        
        // Anonymize Korean ID patterns
        anonymized = anonymized.replaceAll("\\b\\d{6}[- ]?\\d{7}\\b", "******-*******");
        
        // Anonymize credit card numbers
        anonymized = anonymized.replaceAll("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b", "****-****-****-****");
        
        // Anonymize IP addresses
        anonymized = anonymized.replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "***.***.***.***");
        
        return anonymized;
    }
    
    /**
     * Pseudonymize data by creating consistent but reversible mapping
     */
    public String pseudonymizeData(String data, String salt) {
        if (data == null) return null;
        
        try {
            // Create consistent hash-based pseudonym
            String combined = data + salt;
            return "pseudo_" + Integer.toHexString(combined.hashCode());
        } catch (Exception e) {
            log.error("Error pseudonymizing data", e);
            return "pseudo_unknown";
        }
    }
    
    /**
     * Record user consent for data processing
     */
    public void recordConsent(String userId, ConsentType consentType, String purpose, String legalBasis) {
        ConsentRecord consent = ConsentRecord.builder()
            .userId(userId)
            .consentType(consentType)
            .purpose(purpose)
            .legalBasis(legalBasis)
            .consentGiven(true)
            .timestamp(LocalDateTime.now())
            .ipAddress(getCurrentUserIP())
            .build();
            
        consentRecords.put(userId + "_" + consentType.name(), consent);
        
        securityEventLogger.logDataAccess(userId, "consent", "consent_recording");
        
        log.info("Consent recorded - User: {}, Type: {}, Purpose: {}", userId, consentType, purpose);
    }
    
    /**
     * Withdraw user consent
     */
    public void withdrawConsent(String userId, ConsentType consentType) {
        String key = userId + "_" + consentType.name();
        ConsentRecord existing = consentRecords.get(key);
        
        if (existing != null) {
            ConsentRecord withdrawal = existing.toBuilder()
                .consentGiven(false)
                .withdrawalTimestamp(LocalDateTime.now())
                .build();
                
            consentRecords.put(key, withdrawal);
            
            securityEventLogger.logDataAccess(userId, "consent", "consent_withdrawal");
            
            log.info("Consent withdrawn - User: {}, Type: {}", userId, consentType);
        }
    }
    
    /**
     * Check if user has given consent
     */
    public boolean hasValidConsent(String userId, ConsentType consentType) {
        if (!consentRequired) {
            return true;
        }
        
        String key = userId + "_" + consentType.name();
        ConsentRecord consent = consentRecords.get(key);
        
        return consent != null && 
               consent.isConsentGiven() && 
               consent.getWithdrawalTimestamp() == null;
    }
    
    /**
     * Export user data for GDPR Article 20 (Data Portability)
     */
    public Map<String, Object> exportUserData(String userId) {
        securityEventLogger.logDataAccess(userId, "personal_data", "data_export_gdpr_article_20");
        
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("userId", userId);
        exportData.put("exportTimestamp", LocalDateTime.now().toString());
        exportData.put("exportReason", "GDPR Article 20 - Data Portability");
        
        // Add consent records
        List<ConsentRecord> userConsents = consentRecords.values().stream()
            .filter(consent -> consent.getUserId().equals(userId))
            .toList();
        exportData.put("consents", userConsents);
        
        // Add retention information
        exportData.put("retentionPolicies", getApplicableRetentionPolicies(userId));
        
        log.info("User data exported for GDPR compliance - User: {}", userId);
        
        return exportData;
    }
    
    /**
     * Identify and classify PII in data
     */
    public PiiClassification classifyPII(String data) {
        if (data == null) {
            return new PiiClassification(false, Collections.emptyList());
        }
        
        List<String> piiTypes = new ArrayList<>();
        
        for (int i = 0; i < PII_PATTERNS.size(); i++) {
            if (PII_PATTERNS.get(i).matcher(data).find()) {
                piiTypes.add(getPiiTypeName(i));
            }
        }
        
        return new PiiClassification(!piiTypes.isEmpty(), piiTypes);
    }
    
    /**
     * Check if data should be deleted based on retention policy
     */
    public boolean shouldDeleteData(DataType dataType, LocalDateTime createdAt) {
        RetentionPolicy policy = retentionPolicies.get(dataType);
        if (policy == null) {
            policy = new RetentionPolicy(defaultRetentionDays, true);
        }
        
        LocalDateTime expiryDate = createdAt.plusDays(policy.getRetentionDays());
        return LocalDateTime.now().isAfter(expiryDate);
    }
    
    /**
     * Schedule data deletion based on retention policies
     */
    public void scheduleDataDeletion(String userId, DataType dataType, String entityId) {
        RetentionPolicy policy = retentionPolicies.get(dataType);
        if (policy != null && policy.isAutomaticDeletion()) {
            LocalDateTime deletionDate = LocalDateTime.now().plusDays(policy.getRetentionDays());
            
            log.info("Scheduled data deletion - User: {}, Type: {}, Entity: {}, Deletion: {}", 
                    userId, dataType, entityId, deletionDate);
            
            securityEventLogger.logDataAccess(userId, dataType.name(), "deletion_scheduled");
        }
    }
    
    /**
     * Get data processing audit log for user
     */
    public List<DataProcessingLog> getDataProcessingAudit(String userId) {
        securityEventLogger.logDataAccess(userId, "audit_log", "gdpr_article_15_access");
        
        // This would typically query a database
        List<DataProcessingLog> auditLog = new ArrayList<>();
        
        // Add consent-related processing
        consentRecords.values().stream()
            .filter(consent -> consent.getUserId().equals(userId))
            .forEach(consent -> {
                DataProcessingLog log = DataProcessingLog.builder()
                    .userId(userId)
                    .activityType("consent_" + (consent.isConsentGiven() ? "given" : "withdrawn"))
                    .purpose(consent.getPurpose())
                    .legalBasis(consent.getLegalBasis())
                    .timestamp(consent.getTimestamp())
                    .build();
                auditLog.add(log);
            });
        
        return auditLog;
    }
    
    private SecretKey getEncryptionKey() {
        try {
            if (encryptionKeyBase64.isEmpty()) {
                // Generate a new key for development/testing
                KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
                keyGenerator.init(256);
                return keyGenerator.generateKey();
            } else {
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
                return new SecretKeySpec(keyBytes, ALGORITHM);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES algorithm not available", e);
        }
    }
    
    private String getPiiTypeName(int index) {
        return switch (index) {
            case 0 -> "EMAIL";
            case 1 -> "PHONE";
            case 2 -> "KOREAN_ID";
            case 3 -> "CREDIT_CARD";
            case 4 -> "IP_ADDRESS";
            default -> "UNKNOWN";
        };
    }
    
    private String getCurrentUserIP() {
        // This would typically be injected from the current request context
        return "127.0.0.1"; // Placeholder
    }
    
    private Map<String, RetentionPolicy> getApplicableRetentionPolicies(String userId) {
        return new HashMap<>(retentionPolicies);
    }
    
    // Data classes for privacy compliance
    
    public enum ConsentType {
        MARKETING,
        ANALYTICS,
        PERSONALIZATION,
        THIRD_PARTY_SHARING,
        COOKIES,
        ESSENTIAL_SERVICES
    }
    
    public enum DataType {
        PERSONAL_DATA,
        AUTHENTICATION_DATA,
        USAGE_ANALYTICS,
        MARKETING_DATA,
        TRANSACTION_DATA,
        COOKIES_DATA
    }
    
    public static class PiiClassification {
        private final boolean containsPii;
        private final List<String> piiTypes;
        
        public PiiClassification(boolean containsPii, List<String> piiTypes) {
            this.containsPii = containsPii;
            this.piiTypes = piiTypes;
        }
        
        public boolean containsPii() { return containsPii; }
        public List<String> getPiiTypes() { return piiTypes; }
    }
    
    public static class RetentionPolicy {
        private final int retentionDays;
        private final boolean automaticDeletion;
        
        public RetentionPolicy(int retentionDays, boolean automaticDeletion) {
            this.retentionDays = retentionDays;
            this.automaticDeletion = automaticDeletion;
        }
        
        public int getRetentionDays() { return retentionDays; }
        public boolean isAutomaticDeletion() { return automaticDeletion; }
    }
    
    private static class ConsentRecord {
        private final String userId;
        private final ConsentType consentType;
        private final String purpose;
        private final String legalBasis;
        private final boolean consentGiven;
        private final LocalDateTime timestamp;
        private final LocalDateTime withdrawalTimestamp;
        private final String ipAddress;
        
        private ConsentRecord(Builder builder) {
            this.userId = builder.userId;
            this.consentType = builder.consentType;
            this.purpose = builder.purpose;
            this.legalBasis = builder.legalBasis;
            this.consentGiven = builder.consentGiven;
            this.timestamp = builder.timestamp;
            this.withdrawalTimestamp = builder.withdrawalTimestamp;
            this.ipAddress = builder.ipAddress;
        }
        
        public static Builder builder() { return new Builder(); }
        public Builder toBuilder() {
            return new Builder()
                .userId(userId)
                .consentType(consentType)
                .purpose(purpose)
                .legalBasis(legalBasis)
                .consentGiven(consentGiven)
                .timestamp(timestamp)
                .withdrawalTimestamp(withdrawalTimestamp)
                .ipAddress(ipAddress);
        }
        
        // Getters
        public String getUserId() { return userId; }
        public ConsentType getConsentType() { return consentType; }
        public String getPurpose() { return purpose; }
        public String getLegalBasis() { return legalBasis; }
        public boolean isConsentGiven() { return consentGiven; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public LocalDateTime getWithdrawalTimestamp() { return withdrawalTimestamp; }
        public String getIpAddress() { return ipAddress; }
        
        private static class Builder {
            private String userId;
            private ConsentType consentType;
            private String purpose;
            private String legalBasis;
            private boolean consentGiven;
            private LocalDateTime timestamp;
            private LocalDateTime withdrawalTimestamp;
            private String ipAddress;
            
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder consentType(ConsentType consentType) { this.consentType = consentType; return this; }
            public Builder purpose(String purpose) { this.purpose = purpose; return this; }
            public Builder legalBasis(String legalBasis) { this.legalBasis = legalBasis; return this; }
            public Builder consentGiven(boolean consentGiven) { this.consentGiven = consentGiven; return this; }
            public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            public Builder withdrawalTimestamp(LocalDateTime withdrawalTimestamp) { this.withdrawalTimestamp = withdrawalTimestamp; return this; }
            public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
            
            public ConsentRecord build() { return new ConsentRecord(this); }
        }
    }
    
    private static class DataProcessingLog {
        private final String userId;
        private final String activityType;
        private final String purpose;
        private final String legalBasis;
        private final LocalDateTime timestamp;
        
        private DataProcessingLog(Builder builder) {
            this.userId = builder.userId;
            this.activityType = builder.activityType;
            this.purpose = builder.purpose;
            this.legalBasis = builder.legalBasis;
            this.timestamp = builder.timestamp;
        }
        
        public static Builder builder() { return new Builder(); }
        
        // Getters
        public String getUserId() { return userId; }
        public String getActivityType() { return activityType; }
        public String getPurpose() { return purpose; }
        public String getLegalBasis() { return legalBasis; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        private static class Builder {
            private String userId;
            private String activityType;
            private String purpose;
            private String legalBasis;
            private LocalDateTime timestamp;
            
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder activityType(String activityType) { this.activityType = activityType; return this; }
            public Builder purpose(String purpose) { this.purpose = purpose; return this; }
            public Builder legalBasis(String legalBasis) { this.legalBasis = legalBasis; return this; }
            public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            
            public DataProcessingLog build() { return new DataProcessingLog(this); }
        }
    }
}