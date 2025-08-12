package com.oddiya.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Security Validation Utilities
 * 
 * Comprehensive input validation and sanitization to prevent:
 * - SQL Injection (A03: Injection)
 * - XSS (A03: Injection) 
 * - SSRF (A10: Server-Side Request Forgery)
 * - Path Traversal (A01: Broken Access Control)
 * - Command Injection (A03: Injection)
 * - Header Injection
 * - LDAP Injection
 * 
 * OWASP Top 10 2021 Coverage:
 * - A01: Broken Access Control
 * - A03: Injection
 * - A10: Server-Side Request Forgery (SSRF)
 */
@Slf4j
@Component
public class SecurityValidationUtils {
    
    // SQL Injection patterns
    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i).*(\\bUNION\\b.*\\bSELECT\\b|\\bSELECT\\b.*\\bFROM\\b|\\bINSERT\\b.*\\bINTO\\b|\\bUPDATE\\b.*\\bSET\\b|\\bDELETE\\b.*\\bFROM\\b).*"),
        Pattern.compile("(?i).*(\\bDROP\\b.*\\bTABLE\\b|\\bCREATE\\b.*\\bTABLE\\b|\\bALTER\\b.*\\bTABLE\\b).*"),
        Pattern.compile("(?i).*(\\bEXEC\\b|\\bEXECUTE\\b|\\bsp_\\w+|\\bxp_\\w+).*"),
        Pattern.compile("(?i).*(-{2}|/\\*|\\*/|;\\s*$|'\\s*OR\\s*'.*'\\s*=\\s*'|'\\s*OR\\s*1\\s*=\\s*1).*"),
        Pattern.compile("(?i).*(\\bCAST\\b|\\bCONVERT\\b|\\bCHAR\\b|\\bASCII\\b|\\bORD\\b)\\s*\\(.*"),
        Pattern.compile("(?i).*(\\bSLEEP\\b|\\bBENCHMARK\\b|\\bWAITFOR\\b|\\bDELAY\\b)\\s*\\(.*")
    );
    
    // XSS patterns
    private static final List<Pattern> XSS_PATTERNS = List.of(
        Pattern.compile("(?i).*<\\s*script[^>]*>.*", Pattern.DOTALL),
        Pattern.compile("(?i).*<\\s*iframe[^>]*>.*", Pattern.DOTALL),
        Pattern.compile("(?i).*<\\s*object[^>]*>.*", Pattern.DOTALL),
        Pattern.compile("(?i).*<\\s*embed[^>]*>.*", Pattern.DOTALL),
        Pattern.compile("(?i).*<\\s*applet[^>]*>.*", Pattern.DOTALL),
        Pattern.compile("(?i).*<\\s*meta[^>]*>.*", Pattern.DOTALL),
        Pattern.compile("(?i).*<\\s*link[^>]*>.*", Pattern.DOTALL),
        Pattern.compile("(?i).*on\\w+\\s*=.*", Pattern.DOTALL),
        Pattern.compile("(?i).*javascript\\s*:.*", Pattern.DOTALL),
        Pattern.compile("(?i).*vbscript\\s*:.*", Pattern.DOTALL),
        Pattern.compile("(?i).*data\\s*:.*text/html.*", Pattern.DOTALL),
        Pattern.compile("(?i).*expression\\s*\\(.*", Pattern.DOTALL)
    );
    
    // Command injection patterns
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = List.of(
        Pattern.compile(".*[;&|`$\\(\\){}\\[\\]<>].*"),
        Pattern.compile("(?i).*(\\bcat\\b|\\bls\\b|\\bps\\b|\\bwhoami\\b|\\bpwd\\b|\\bchmod\\b|\\brm\\b|\\bmv\\b|\\bcp\\b).*"),
        Pattern.compile("(?i).*(\\bcmd\\b|\\bpowershell\\b|\\bash\\b|\\bzsh\\b|\\bfish\\b).*"),
        Pattern.compile("(?i).*(\\beval\\b|\\bexec\\b|\\bsystem\\b|\\bshell_exec\\b).*")
    );
    
    // Path traversal patterns
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = List.of(
        Pattern.compile(".*\\.{2}[/\\\\].*"),
        Pattern.compile(".*[/\\\\]\\.{2}.*"),
        Pattern.compile("(?i).*(\\bWEB-INF\\b|\\bMETA-INF\\b|\\betc/passwd\\b|\\bboot\\.ini\\b|\\bwin\\.ini\\b).*"),
        Pattern.compile(".*\\x00.*"), // Null byte injection
        Pattern.compile("(?i).*\\\\\\\\.*") // UNC path
    );
    
    // SSRF dangerous URLs
    private static final List<Pattern> SSRF_PATTERNS = List.of(
        Pattern.compile("(?i).*(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|::1|0:0:0:0:0:0:0:1).*"),
        Pattern.compile("(?i).*(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.).*"), // Private IP ranges
        Pattern.compile("(?i).*(169\\.254\\.).*"), // Link-local addresses
        Pattern.compile("(?i).*(file://|ftp://|gopher://|dict://|ldap://|tftp://).*"),
        Pattern.compile("(?i).*(jar://|netdoc://|mailto:).*")
    );
    
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    // Safe filename pattern
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._-]+\\.[a-zA-Z0-9]+$"
    );
    
    // UUID pattern
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    
    /**
     * Validate input against SQL injection attacks
     */
    public boolean isSqlInjectionSafe(String input) {
        if (input == null) return true;
        
        String cleanInput = input.trim();
        if (cleanInput.isEmpty()) return true;
        
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(cleanInput).matches()) {
                log.warn("SQL injection attempt detected: {}", maskSensitiveInput(cleanInput));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate input against XSS attacks
     */
    public boolean isXssSafe(String input) {
        if (input == null) return true;
        
        String cleanInput = input.trim();
        if (cleanInput.isEmpty()) return true;
        
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(cleanInput).matches()) {
                log.warn("XSS attempt detected: {}", maskSensitiveInput(cleanInput));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate input against command injection attacks
     */
    public boolean isCommandInjectionSafe(String input) {
        if (input == null) return true;
        
        String cleanInput = input.trim();
        if (cleanInput.isEmpty()) return true;
        
        for (Pattern pattern : COMMAND_INJECTION_PATTERNS) {
            if (pattern.matcher(cleanInput).matches()) {
                log.warn("Command injection attempt detected: {}", maskSensitiveInput(cleanInput));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate input against path traversal attacks
     */
    public boolean isPathTraversalSafe(String input) {
        if (input == null) return true;
        
        String cleanInput = input.trim();
        if (cleanInput.isEmpty()) return true;
        
        for (Pattern pattern : PATH_TRAVERSAL_PATTERNS) {
            if (pattern.matcher(cleanInput).matches()) {
                log.warn("Path traversal attempt detected: {}", maskSensitiveInput(cleanInput));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate URL against SSRF attacks
     */
    public boolean isSsrfSafe(String url) {
        if (url == null) return true;
        
        String cleanUrl = url.trim().toLowerCase();
        if (cleanUrl.isEmpty()) return true;
        
        // Check against SSRF patterns
        for (Pattern pattern : SSRF_PATTERNS) {
            if (pattern.matcher(cleanUrl).matches()) {
                log.warn("SSRF attempt detected: {}", maskSensitiveInput(url));
                return false;
            }
        }
        
        // Validate URL structure
        try {
            URI uri = new URI(cleanUrl);
            URL validUrl = uri.toURL();
            
            // Only allow HTTP and HTTPS
            String protocol = validUrl.getProtocol().toLowerCase();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                log.warn("Unsafe protocol detected: {}", protocol);
                return false;
            }
            
            String host = validUrl.getHost();
            if (host == null) {
                return false;
            }
            
            // Additional IP address checks
            if (isPrivateOrLocalhost(host)) {
                log.warn("Private/localhost URL detected: {}", host);
                return false;
            }
            
            return true;
            
        } catch (URISyntaxException | MalformedURLException e) {
            log.warn("Invalid URL format: {}", maskSensitiveInput(url));
            return false;
        }
    }
    
    /**
     * Comprehensive input validation
     */
    public boolean isInputSafe(String input) {
        return isSqlInjectionSafe(input) && 
               isXssSafe(input) && 
               isCommandInjectionSafe(input) && 
               isPathTraversalSafe(input);
    }
    
    /**
     * Sanitize HTML content by removing dangerous elements
     */
    public String sanitizeHtml(String html) {
        if (html == null) return null;
        
        // Remove script tags and their content
        html = html.replaceAll("(?i)<script[^>]*>.*?</script>", "");
        
        // Remove dangerous event handlers
        html = html.replaceAll("(?i)\\son\\w+\\s*=\\s*[\"'][^\"']*[\"']", "");
        
        // Remove javascript: and vbscript: protocols
        html = html.replaceAll("(?i)javascript\\s*:", "");
        html = html.replaceAll("(?i)vbscript\\s*:", "");
        
        // Remove dangerous tags
        html = html.replaceAll("(?i)<(iframe|object|embed|applet|meta|link)[^>]*>.*?</\\1>", "");
        html = html.replaceAll("(?i)<(iframe|object|embed|applet|meta|link)[^>]*/>", "");
        
        return html;
    }
    
    /**
     * Validate email format
     */
    public boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
    
    /**
     * Validate filename for safe file operations
     */
    public boolean isSafeFilename(String filename) {
        if (filename == null) return false;
        
        String cleanFilename = filename.trim();
        if (cleanFilename.isEmpty()) return false;
        
        return SAFE_FILENAME_PATTERN.matcher(cleanFilename).matches() && 
               isPathTraversalSafe(cleanFilename);
    }
    
    /**
     * Validate UUID format
     */
    public boolean isValidUuid(String uuid) {
        if (uuid == null) return false;
        return UUID_PATTERN.matcher(uuid.trim()).matches();
    }
    
    /**
     * Validate Base64 encoded data
     */
    public boolean isValidBase64(String base64) {
        if (base64 == null) return false;
        
        try {
            Base64.getDecoder().decode(base64);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Check if input contains only alphanumeric characters
     */
    public boolean isAlphanumeric(String input) {
        if (input == null) return false;
        return input.matches("^[a-zA-Z0-9]+$");
    }
    
    /**
     * Check if input is within length limits
     */
    public boolean isValidLength(String input, int minLength, int maxLength) {
        if (input == null) return minLength <= 0;
        
        int length = input.trim().length();
        return length >= minLength && length <= maxLength;
    }
    
    /**
     * Escape special characters for safe output
     */
    public String escapeHtml(String input) {
        if (input == null) return null;
        
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * Validate JSON structure (basic validation)
     */
    public boolean isValidJsonStructure(String json) {
        if (json == null) return false;
        
        json = json.trim();
        return (json.startsWith("{") && json.endsWith("}")) || 
               (json.startsWith("[") && json.endsWith("]"));
    }
    
    private boolean isPrivateOrLocalhost(String host) {
        // Check for localhost variations
        if (host.equals("localhost") || host.equals("127.0.0.1") || 
            host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        
        // Check for private IP ranges
        if (host.matches("10\\..*") || 
            host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*") ||
            host.matches("192\\.168\\..*") ||
            host.matches("169\\.254\\..*")) {
            return true;
        }
        
        return false;
    }
    
    private String maskSensitiveInput(String input) {
        if (input == null || input.length() <= 10) {
            return "***";
        }
        
        return input.substring(0, 5) + "***" + input.substring(input.length() - 5);
    }
}