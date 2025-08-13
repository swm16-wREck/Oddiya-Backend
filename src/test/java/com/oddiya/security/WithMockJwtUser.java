package com.oddiya.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Custom annotation for mocking JWT-authenticated users in tests
 * This creates a proper SecurityContext with JWT-like authentication
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockJwtUserSecurityContextFactory.class)
public @interface WithMockJwtUser {
    
    /**
     * The username/userId to set in the security context
     */
    String value() default "test-user-id";
    
    /**
     * The email to associate with the user
     */
    String email() default "test@example.com";
    
    /**
     * Additional roles for the user (if needed in future)
     */
    String[] roles() default {};
}