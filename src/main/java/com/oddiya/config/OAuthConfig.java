package com.oddiya.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oauth")
public class OAuthConfig {
    
    private Google google = new Google();
    private Apple apple = new Apple();
    
    @Data
    public static class Google {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String tokenUrl = "https://oauth2.googleapis.com/token";
        private String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";
    }
    
    @Data
    public static class Apple {
        private String clientId;
        private String teamId;
        private String keyId;
        private String privateKey;
        private String redirectUri;
        private String tokenUrl = "https://appleid.apple.com/auth/token";
        private String keysUrl = "https://appleid.apple.com/auth/keys";
    }
}