package com.oddiya.contract.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.oddiya.contract.ContractTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for external service contract tests using WireMock
 */
@TestPropertySource(properties = {
    "app.supabase.url=http://localhost:${wiremock.server.port}/supabase",
    "app.naver.maps.url=http://localhost:${wiremock.server.port}/naver-maps",
    "aws.s3.endpoint=http://localhost:${wiremock.server.port}/s3",
    "aws.sqs.endpoint=http://localhost:${wiremock.server.port}/sqs",
    "aws.bedrock.endpoint=http://localhost:${wiremock.server.port}/bedrock"
})
public abstract class ExternalServiceContractTestBase extends ContractTestBase {

    protected WireMockServer wireMockServer;
    protected static final int WIREMOCK_PORT = 8089;

    @BeforeEach
    public void setupWireMock() {
        wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                .port(WIREMOCK_PORT)
                .bindAddress("localhost")
        );
        wireMockServer.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);
        setupExternalServiceStubs();
    }

    @AfterEach
    public void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    protected abstract void setupExternalServiceStubs();
}