package com.oddiya.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/v1/video")
@CrossOrigin(origins = "*", maxAge = 3600)
public class VideoProxyController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String VIDEO_API_URL = "http://172.31.10.25:3001"; // Internal IP of EC2

    @PostMapping("/render")
    public ResponseEntity<?> proxyRender(@RequestBody Map<String, Object> payload) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Video generation failed", "message", e.getMessage()));
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