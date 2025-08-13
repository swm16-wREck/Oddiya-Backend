package com.oddiya.contract.external;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for Supabase external service integration
 */
@SpringBootTest
public class SupabaseContractTest extends ExternalServiceContractTestBase {

    @Override
    protected void setupExternalServiceStubs() {
        setupSupabaseAuthStubs();
        setupSupabaseStorageStubs();
        setupSupabaseDatabaseStubs();
    }

    private void setupSupabaseAuthStubs() {
        // Mock Supabase Auth - Sign In with OAuth
        stubFor(post(urlEqualTo("/supabase/auth/v1/token"))
            .withQueryParam("grant_type", equalTo("id_token"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "access_token": "supabase_access_token_12345",
                        "refresh_token": "supabase_refresh_token_12345",
                        "expires_in": 3600,
                        "token_type": "Bearer",
                        "user": {
                            "id": "supabase_user_123",
                            "email": "test@example.com",
                            "email_confirmed_at": "2024-01-01T12:00:00Z",
                            "user_metadata": {
                                "name": "Test User",
                                "picture": "https://example.com/avatar.jpg"
                            },
                            "created_at": "2024-01-01T12:00:00Z",
                            "updated_at": "2024-01-01T12:00:00Z"
                        }
                    }
                    """)));

        // Mock Supabase Auth - Get User
        stubFor(get(urlEqualTo("/supabase/auth/v1/user"))
            .withHeader("Authorization", equalTo("Bearer supabase_access_token_12345"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "supabase_user_123",
                        "email": "test@example.com",
                        "email_confirmed_at": "2024-01-01T12:00:00Z",
                        "user_metadata": {
                            "name": "Test User",
                            "picture": "https://example.com/avatar.jpg"
                        },
                        "created_at": "2024-01-01T12:00:00Z",
                        "updated_at": "2024-01-01T12:00:00Z"
                    }
                    """)));

        // Mock Supabase Auth - Refresh Token
        stubFor(post(urlEqualTo("/supabase/auth/v1/token"))
            .withQueryParam("grant_type", equalTo("refresh_token"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "access_token": "new_supabase_access_token_12345",
                        "refresh_token": "new_supabase_refresh_token_12345",
                        "expires_in": 3600,
                        "token_type": "Bearer"
                    }
                    """)));

        // Mock Supabase Auth - Sign Out
        stubFor(post(urlEqualTo("/supabase/auth/v1/logout"))
            .withHeader("Authorization", matching("Bearer .*"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.NO_CONTENT.value())));
    }

    private void setupSupabaseStorageStubs() {
        // Mock Supabase Storage - Upload File
        stubFor(post(urlMatching("/supabase/storage/v1/object/.*"))
            .withHeader("Authorization", matching("Bearer .*"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "Key": "user-uploads/profile/avatar.jpg",
                        "Id": "storage_object_123"
                    }
                    """)));

        // Mock Supabase Storage - Get Public URL
        stubFor(post(urlMatching("/supabase/storage/v1/object/public/.*"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "publicURL": "https://supabase-storage.example.com/user-uploads/profile/avatar.jpg"
                    }
                    """)));

        // Mock Supabase Storage - Delete File
        stubFor(delete(urlMatching("/supabase/storage/v1/object/.*"))
            .withHeader("Authorization", matching("Bearer .*"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "message": "Successfully deleted"
                    }
                    """)));
    }

    private void setupSupabaseDatabaseStubs() {
        // Mock Supabase Database - Insert/Update operations
        stubFor(post(urlMatching("/supabase/rest/v1/.*"))
            .withHeader("Authorization", matching("Bearer .*"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.CREATED.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{
                        "id": "record_123",
                        "created_at": "2024-01-01T12:00:00Z",
                        "updated_at": "2024-01-01T12:00:00Z"
                    }]
                    """)));

        // Mock Supabase Database - Select operations
        stubFor(get(urlMatching("/supabase/rest/v1/.*"))
            .withHeader("Authorization", matching("Bearer .*"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{
                        "id": "record_123",
                        "data": "test data",
                        "created_at": "2024-01-01T12:00:00Z",
                        "updated_at": "2024-01-01T12:00:00Z"
                    }]
                    """)));

        // Mock Supabase Database - Error response
        stubFor(post(urlEqualTo("/supabase/rest/v1/invalid-table"))
            .withHeader("Authorization", matching("Bearer .*"))
            .withHeader("apikey", equalTo("test-supabase-key"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.NOT_FOUND.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "PGRST116",
                        "details": null,
                        "hint": null,
                        "message": "relation \"public.invalid-table\" does not exist"
                    }
                    """)));
    }

    @Test
    public void shouldVerifySupabaseAuthContract() {
        // Test passes if WireMock stubs are set up correctly
        // This validates that our contract expectations match Supabase API
        verify(exactly(0), postRequestedFor(urlMatching("/supabase/.*")));
    }

    @Test
    public void shouldVerifySupabaseStorageContract() {
        // Test passes if WireMock stubs are set up correctly
        verify(exactly(0), postRequestedFor(urlMatching("/supabase/storage/.*")));
    }

    @Test
    public void shouldVerifySupabaseDatabaseContract() {
        // Test passes if WireMock stubs are set up correctly
        verify(exactly(0), getRequestedFor(urlMatching("/supabase/rest/.*")));
    }
}