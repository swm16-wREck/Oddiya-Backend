package com.oddiya.controller;

import com.oddiya.dto.message.*;
import com.oddiya.dto.response.ApiResponse;
import com.oddiya.service.messaging.LocalMessagingService;
import com.oddiya.service.messaging.MessagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for messaging system administration and monitoring
 */
@RestController
@RequestMapping("/api/v1/messaging")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Messaging", description = "Message queue administration and monitoring endpoints")
public class MessagingController {

    private final MessagingService messagingService;

    @Autowired(required = false)
    private LocalMessagingService localMessagingService;

    /**
     * Send an email message
     */
    @PostMapping("/email")
    @Operation(
        summary = "Send email message",
        description = "Send an email message to the email notification queue"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<String>> sendEmailMessage(
            @Valid @RequestBody EmailMessage emailMessage) {
        
        try {
            CompletableFuture<Void> future = messagingService.sendEmailMessage(emailMessage);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Email message sent successfully. Message ID: " + emailMessage.getMessageId()
            ));
        } catch (Exception e) {
            log.error("Error sending email message: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to send email message: " + e.getMessage()));
        }
    }

    /**
     * Send an image processing message
     */
    @PostMapping("/image-processing")
    @Operation(
        summary = "Send image processing message",
        description = "Send an image processing message to the image processing queue"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<String>> sendImageProcessingMessage(
            @Valid @RequestBody ImageProcessingMessage imageMessage) {
        
        try {
            CompletableFuture<Void> future = messagingService.sendImageProcessingMessage(imageMessage);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Image processing message sent successfully. Message ID: " + imageMessage.getMessageId()
            ));
        } catch (Exception e) {
            log.error("Error sending image processing message: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to send image processing message: " + e.getMessage()));
        }
    }

    /**
     * Send an analytics message
     */
    @PostMapping("/analytics")
    @Operation(
        summary = "Send analytics message",
        description = "Send an analytics message to the analytics events queue"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<String>> sendAnalyticsMessage(
            @Valid @RequestBody AnalyticsMessage analyticsMessage) {
        
        try {
            CompletableFuture<Void> future = messagingService.sendAnalyticsMessage(analyticsMessage);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Analytics message sent successfully. Message ID: " + analyticsMessage.getMessageId()
            ));
        } catch (Exception e) {
            log.error("Error sending analytics message: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to send analytics message: " + e.getMessage()));
        }
    }

    /**
     * Send a recommendation message
     */
    @PostMapping("/recommendation")
    @Operation(
        summary = "Send recommendation message",
        description = "Send a recommendation message to the recommendation updates queue"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<String>> sendRecommendationMessage(
            @Valid @RequestBody RecommendationMessage recommendationMessage) {
        
        try {
            CompletableFuture<Void> future = messagingService.sendRecommendationMessage(recommendationMessage);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Recommendation message sent successfully. Message ID: " + recommendationMessage.getMessageId()
            ));
        } catch (Exception e) {
            log.error("Error sending recommendation message: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to send recommendation message: " + e.getMessage()));
        }
    }

    /**
     * Send a video processing message
     */
    @PostMapping("/video-processing")
    @Operation(
        summary = "Send video processing message",
        description = "Send a video processing message to the video processing queue"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<String>> sendVideoProcessingMessage(
            @Valid @RequestBody VideoProcessingMessage videoMessage) {
        
        try {
            CompletableFuture<Void> future = messagingService.sendVideoProcessingMessage(videoMessage);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Video processing message sent successfully. Message ID: " + videoMessage.getMessageId()
            ));
        } catch (Exception e) {
            log.error("Error sending video processing message: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to send video processing message: " + e.getMessage()));
        }
    }

    /**
     * Send batch email messages
     */
    @PostMapping("/email/batch")
    @Operation(
        summary = "Send batch email messages",
        description = "Send multiple email messages in batch"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<String>> sendBatchEmailMessages(
            @Valid @RequestBody List<EmailMessage> emailMessages) {
        
        try {
            CompletableFuture<Void> future = messagingService.sendBatchEmailMessages(emailMessages);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Batch email messages sent successfully. Message count: " + emailMessages.size()
            ));
        } catch (Exception e) {
            log.error("Error sending batch email messages: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to send batch email messages: " + e.getMessage()));
        }
    }

    /**
     * Get queue statistics
     */
    @GetMapping("/queues/stats")
    @Operation(
        summary = "Get queue statistics",
        description = "Get statistics for all message queues"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueueStatistics() {
        
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Get stats for all known queues
            String[] queueNames = {
                "oddiya-email-notifications",
                "oddiya-image-processing", 
                "oddiya-analytics-events",
                "oddiya-recommendation-updates",
                "oddiya-video-processing"
            };

            for (String queueName : queueNames) {
                Map<String, Object> queueStats = new HashMap<>();
                queueStats.put("queueName", queueName);
                queueStats.put("messageCount", messagingService.getMessageCount(queueName));
                queueStats.put("queueUrl", messagingService.getQueueUrl(queueName));
                queueStats.put("exists", messagingService.queueExists(queueName));
                queueStats.put("attributes", messagingService.getQueueAttributes(queueName));
                queueStats.put("lastChecked", LocalDateTime.now());
                
                stats.put(queueName, queueStats);
            }

            // Add local service stats if available
            if (localMessagingService != null) {
                Map<String, Object> localStats = localMessagingService.getQueueStatistics();
                stats.put("localServiceStats", localStats);
            }

            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("Error getting queue statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to get queue statistics: " + e.getMessage()));
        }
    }

    /**
     * Get specific queue information
     */
    @GetMapping("/queues/{queueName}")
    @Operation(
        summary = "Get queue information",
        description = "Get detailed information about a specific queue"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueueInfo(
            @Parameter(description = "Queue name", required = true)
            @PathVariable String queueName) {
        
        try {
            Map<String, Object> queueInfo = new HashMap<>();
            queueInfo.put("queueName", queueName);
            queueInfo.put("exists", messagingService.queueExists(queueName));
            
            if (messagingService.queueExists(queueName)) {
                queueInfo.put("messageCount", messagingService.getMessageCount(queueName));
                queueInfo.put("queueUrl", messagingService.getQueueUrl(queueName));
                queueInfo.put("attributes", messagingService.getQueueAttributes(queueName));
            }
            
            queueInfo.put("lastChecked", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success(queueInfo));
        } catch (Exception e) {
            log.error("Error getting queue info for {}: {}", queueName, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to get queue information: " + e.getMessage()));
        }
    }

    /**
     * Receive messages from a queue (for testing/debugging)
     */
    @GetMapping("/queues/{queueName}/messages")
    @Operation(
        summary = "Receive messages from queue",
        description = "Receive messages from a specific queue (for testing/debugging)"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> receiveMessages(
            @Parameter(description = "Queue name", required = true)
            @PathVariable String queueName,
            @Parameter(description = "Maximum number of messages to receive", required = false)
            @RequestParam(defaultValue = "10") int maxMessages) {
        
        try {
            List<String> messages = messagingService.receiveMessages(queueName, maxMessages);
            
            return ResponseEntity.ok(ApiResponse.success(messages));
        } catch (Exception e) {
            log.error("Error receiving messages from queue {}: {}", queueName, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to receive messages: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(
        summary = "Messaging service health check",
        description = "Check the health status of the messaging service"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        
        try {
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("timestamp", LocalDateTime.now());
            healthInfo.put("service", "MessagingService");
            
            // Check if main queues exist
            String[] criticalQueues = {
                "oddiya-email-notifications",
                "oddiya-analytics-events"
            };
            
            boolean allQueuesHealthy = true;
            Map<String, Boolean> queueStatus = new HashMap<>();
            
            for (String queueName : criticalQueues) {
                boolean exists = messagingService.queueExists(queueName);
                queueStatus.put(queueName, exists);
                if (!exists) {
                    allQueuesHealthy = false;
                }
            }
            
            healthInfo.put("queues", queueStatus);
            healthInfo.put("status", allQueuesHealthy ? "HEALTHY" : "DEGRADED");
            
            // Add local service status if available
            if (localMessagingService != null) {
                healthInfo.put("localService", "AVAILABLE");
            }
            
            return ResponseEntity.ok(ApiResponse.success(healthInfo));
        } catch (Exception e) {
            log.error("Error in messaging health check: {}", e.getMessage());
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("timestamp", LocalDateTime.now());
            errorInfo.put("service", "MessagingService");
            errorInfo.put("status", "ERROR");
            errorInfo.put("error", e.getMessage());
            
            return ResponseEntity.status(500)
                .body(ApiResponse.error("HEALTH_CHECK_FAILED", "Messaging service health check failed: " + e.getMessage()));
        }
    }

    /**
     * Clear local queue (for testing - only available with LocalMessagingService)
     */
    @DeleteMapping("/queues/{queueName}/clear")
    @Operation(
        summary = "Clear local queue",
        description = "Clear all messages from a local queue (testing only)"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "false", matchIfMissing = true)
    public ResponseEntity<ApiResponse<String>> clearLocalQueue(
            @Parameter(description = "Queue name", required = true)
            @PathVariable String queueName) {
        
        if (localMessagingService == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("SERVICE_UNAVAILABLE", "Local messaging service not available"));
        }

        try {
            localMessagingService.clearQueue(queueName);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Local queue cleared successfully. Queue: " + queueName
            ));
        } catch (Exception e) {
            log.error("Error clearing local queue {}: {}", queueName, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to clear local queue: " + e.getMessage()));
        }
    }

    /**
     * Clear all local queues (for testing - only available with LocalMessagingService)
     */
    @DeleteMapping("/queues/clear-all")
    @Operation(
        summary = "Clear all local queues",
        description = "Clear all messages from all local queues (testing only)"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @ConditionalOnProperty(name = "app.aws.sqs.enabled", havingValue = "false", matchIfMissing = true)
    public ResponseEntity<ApiResponse<String>> clearAllLocalQueues() {
        
        if (localMessagingService == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("SERVICE_UNAVAILABLE", "Local messaging service not available"));
        }

        try {
            localMessagingService.clearAllQueues();
            
            return ResponseEntity.ok(ApiResponse.success(
                "All local queues cleared successfully. All queues have been emptied"
            ));
        } catch (Exception e) {
            log.error("Error clearing all local queues: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to clear all local queues: " + e.getMessage()));
        }
    }

    /**
     * Send a custom message to any queue
     */
    @PostMapping("/queues/{queueName}/message")
    @Operation(
        summary = "Send custom message",
        description = "Send a custom message to a specific queue"
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> sendCustomMessage(
            @Parameter(description = "Queue name", required = true)
            @PathVariable String queueName,
            @RequestBody Object message,
            @Parameter(description = "Message attributes", required = false)
            @RequestParam(required = false) Map<String, String> attributes) {
        
        try {
            CompletableFuture<Void> future;
            
            if (attributes != null && !attributes.isEmpty()) {
                future = messagingService.sendMessage(queueName, message, attributes);
            } else {
                future = messagingService.sendMessage(queueName, message);
            }
            
            return ResponseEntity.ok(ApiResponse.success(
                "Custom message sent successfully. Queue: " + queueName
            ));
        } catch (Exception e) {
            log.error("Error sending custom message to queue {}: {}", queueName, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("MESSAGING_ERROR", "Failed to send custom message: " + e.getMessage()));
        }
    }
}