package com.oddiya.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/video")
@CrossOrigin(origins = "*", maxAge = 3600)
public class VideoProxyController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String VIDEO_API_URL = "http://172.31.10.25:3001"; // Internal IP of EC2
    
    // Sample video URL for demo purposes
    private final String SAMPLE_VIDEO_URL = "https://d2dl9p7inrij8.cloudfront.net/sample-video.mp4";

    @PostMapping("/render")
    public ResponseEntity<?> proxyRender(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Video render request received: {}", payload);
            
            // For now, return a mock response with the sample video URL
            // In production, this would call the actual video generation service
            Map<String, Object> response = Map.of(
                "renderId", "demo-" + System.currentTimeMillis(),
                "status", "success",
                "message", "Video generation completed successfully!",
                "outputUrl", SAMPLE_VIDEO_URL,
                "downloadUrl", "/v1/video/download/sample"
            );
            
            log.info("Video render response: {}", response);
            return ResponseEntity.ok(response);
            
            /* Production code would be:
            String url = VIDEO_API_URL + "/api/lambda/render";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Map.class
            );
            
            return ResponseEntity.ok(response.getBody());
            */
        } catch (Exception e) {
            log.error("Video generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Video generation failed", "message", e.getMessage()));
        }
    }
    
    @GetMapping("/download/{videoId}")
    public ResponseEntity<Resource> downloadVideo(
            @PathVariable String videoId,
            @RequestParam(value = "attachment", defaultValue = "true") boolean attachment) {
        try {
            log.info("Video download request for ID: {}", videoId);
            
            // For demo, stream the sample video from CloudFront
            URL url = URI.create(SAMPLE_VIDEO_URL).toURL();
            InputStream inputStream = url.openStream();
            InputStreamResource resource = new InputStreamResource(inputStream);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("video/mp4"));
            headers.setContentLength(-1); // Unknown length for streaming
            
            if (attachment) {
                headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename("oddiya-video-" + videoId + ".mp4")
                    .build());
            } else {
                headers.setContentDisposition(ContentDisposition.builder("inline")
                    .filename("oddiya-video-" + videoId + ".mp4")
                    .build());
            }
            
            headers.setCacheControl(CacheControl.noCache());
            headers.setPragma("no-cache");
            headers.setExpires(0);
            
            log.info("Streaming video file for download");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
                
        } catch (Exception e) {
            log.error("Failed to download video: {}", videoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try {
            String url = VIDEO_API_URL + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "unhealthy", "error", e.getMessage()));
        }
    }
}