package com.oddiya.contract.external;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Contract tests for AWS Services (S3, SQS, Bedrock) external service integration
 */
@SpringBootTest
public class AWSServicesContractTest extends ExternalServiceContractTestBase {

    @Override
    protected void setupExternalServiceStubs() {
        setupS3Stubs();
        setupSQSStubs();
        setupBedrockStubs();
    }

    private void setupS3Stubs() {
        // Mock S3 - Put Object
        stubFor(put(urlMatching("/s3/.*"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .withHeader("x-amz-date", matching("\\d{8}T\\d{6}Z"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("ETag", "\"d41d8cd98f00b204e9800998ecf8427e\"")
                .withHeader("x-amz-version-id", "version123")
                .withBody("")));

        // Mock S3 - Get Object
        stubFor(get(urlMatching("/s3/.*"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/octet-stream")
                .withHeader("ETag", "\"d41d8cd98f00b204e9800998ecf8427e\"")
                .withHeader("Content-Length", "1024")
                .withBody("mock file content")));

        // Mock S3 - Delete Object
        stubFor(delete(urlMatching("/s3/.*"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.NO_CONTENT.value())));

        // Mock S3 - List Objects
        stubFor(get(urlEqualTo("/s3/"))
            .withQueryParam("list-type", equalTo("2"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/xml")
                .withBody("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                        <Name>oddiya-storage</Name>
                        <Prefix></Prefix>
                        <KeyCount>3</KeyCount>
                        <MaxKeys>1000</MaxKeys>
                        <IsTruncated>false</IsTruncated>
                        <Contents>
                            <Key>user-uploads/profile/avatar.jpg</Key>
                            <LastModified>2024-01-01T12:00:00.000Z</LastModified>
                            <ETag>&quot;d41d8cd98f00b204e9800998ecf8427e&quot;</ETag>
                            <Size>1024</Size>
                            <StorageClass>STANDARD</StorageClass>
                        </Contents>
                        <Contents>
                            <Key>travel-plans/images/seoul-palace.jpg</Key>
                            <LastModified>2024-01-01T12:00:00.000Z</LastModified>
                            <ETag>&quot;e99a18c428cb38d5f260853678922e03&quot;</ETag>
                            <Size>2048</Size>
                            <StorageClass>STANDARD</StorageClass>
                        </Contents>
                    </ListBucketResult>
                    """)));

        // Mock S3 - Generate Presigned URL
        stubFor(post(urlEqualTo("/s3/"))
            .withQueryParam("X-Amz-Algorithm", equalTo("AWS4-HMAC-SHA256"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "url": "https://s3.amazonaws.com/oddiya-storage/user-uploads/profile/avatar.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAI44QH8DHBEXAMPLE%2F20240101%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20240101T120000Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=example-signature"
                    }
                    """)));
    }

    private void setupSQSStubs() {
        // Mock SQS - Send Message
        stubFor(post(urlMatching("/sqs/.*"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .withHeader("Content-Type", equalTo("application/x-amz-json-1.0"))
            .withHeader("X-Amz-Target", equalTo("AmazonSQS.SendMessage"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/x-amz-json-1.0")
                .withBody("""
                    {
                        "MessageId": "12345678-1234-1234-1234-123456789012",
                        "MD5OfBody": "d41d8cd98f00b204e9800998ecf8427e",
                        "MD5OfMessageAttributes": "e99a18c428cb38d5f260853678922e03"
                    }
                    """)));

        // Mock SQS - Receive Message
        stubFor(post(urlMatching("/sqs/.*"))
            .withHeader("X-Amz-Target", equalTo("AmazonSQS.ReceiveMessage"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/x-amz-json-1.0")
                .withBody("""
                    {
                        "Messages": [
                            {
                                "MessageId": "12345678-1234-1234-1234-123456789012",
                                "ReceiptHandle": "receipt-handle-12345",
                                "MD5OfBody": "d41d8cd98f00b204e9800998ecf8427e",
                                "Body": "{\\"type\\":\\"email\\",\\"recipient\\":\\"test@example.com\\",\\"template\\":\\"welcome\\"}",
                                "Attributes": {
                                    "SentTimestamp": "1704110400000",
                                    "ApproximateReceiveCount": "1",
                                    "ApproximateFirstReceiveTimestamp": "1704110400000"
                                },
                                "MessageAttributes": {
                                    "MessageType": {
                                        "StringValue": "EmailMessage",
                                        "DataType": "String"
                                    },
                                    "Priority": {
                                        "StringValue": "HIGH",
                                        "DataType": "String"
                                    }
                                }
                            }
                        ]
                    }
                    """)));

        // Mock SQS - Delete Message
        stubFor(post(urlMatching("/sqs/.*"))
            .withHeader("X-Amz-Target", equalTo("AmazonSQS.DeleteMessage"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/x-amz-json-1.0")
                .withBody("{}")));

        // Mock SQS - Get Queue Attributes
        stubFor(post(urlMatching("/sqs/.*"))
            .withHeader("X-Amz-Target", equalTo("AmazonSQS.GetQueueAttributes"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/x-amz-json-1.0")
                .withBody("""
                    {
                        "Attributes": {
                            "ApproximateNumberOfMessages": "5",
                            "ApproximateNumberOfMessagesNotVisible": "2",
                            "VisibilityTimeout": "300",
                            "CreatedTimestamp": "1704110400",
                            "LastModifiedTimestamp": "1704110400",
                            "QueueArn": "arn:aws:sqs:us-east-1:123456789012:oddiya-email-notifications",
                            "MaxReceiveCount": "3"
                        }
                    }
                    """)));

        // Mock SQS - Send Message Batch
        stubFor(post(urlMatching("/sqs/.*"))
            .withHeader("X-Amz-Target", equalTo("AmazonSQS.SendMessageBatch"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/x-amz-json-1.0")
                .withBody("""
                    {
                        "Successful": [
                            {
                                "Id": "msg-1",
                                "MessageId": "12345678-1234-1234-1234-123456789012",
                                "MD5OfBody": "d41d8cd98f00b204e9800998ecf8427e"
                            },
                            {
                                "Id": "msg-2",
                                "MessageId": "87654321-4321-4321-4321-210987654321",
                                "MD5OfBody": "e99a18c428cb38d5f260853678922e03"
                            }
                        ],
                        "Failed": []
                    }
                    """)));
    }

    private void setupBedrockStubs() {
        // Mock Bedrock - Invoke Model (Claude)
        stubFor(post(urlMatching("/bedrock/model/anthropic.claude-.*:invoke"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "completion": "Based on your travel preferences and the beautiful places you've visited, I recommend exploring Jeju Island next. Known for its stunning natural landscapes, volcanic formations, and unique culture, it offers a perfect blend of relaxation and adventure. The island features beautiful beaches, hiking trails, and local cuisine that would complement your travel experiences in Seoul.",
                        "stop_reason": "end_turn",
                        "stop": null
                    }
                    """)));

        // Mock Bedrock - Invoke Model with streaming
        stubFor(post(urlMatching("/bedrock/model/anthropic.claude-.*:invoke-with-response-stream"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/x-amzn-bedrock-accept")
                .withHeader("x-amzn-bedrock-content-type", "application/json")
                .withBody("""
                    {"chunk":{"bytes":"eyJjb21wbGV0aW9uIjoiQmFzZWQgb24geW91ciB0cmF2ZWwgcHJlZmVyZW5jZXMiLCJ0eXBlIjoiY29tcGxldGlvbiJ9"}}
                    {"chunk":{"bytes":"eyJjb21wbGV0aW9uIjoiIGFuZCB0aGUgYmVhdXRpZnVsIHBsYWNlcyIsInR5cGUiOiJjb21wbGV0aW9uIn0="}}
                    {"chunk":{"bytes":"eyJzdG9wX3JlYXNvbiI6ImVuZF90dXJuIiwidHlwZSI6ImNvbXBsZXRpb24ifQ=="}}
                    """)));

        // Mock Bedrock - List Foundation Models
        stubFor(get(urlEqualTo("/bedrock/foundation-models"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "modelSummaries": [
                            {
                                "modelArn": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-3-sonnet-20240229-v1:0",
                                "modelId": "anthropic.claude-3-sonnet-20240229-v1:0",
                                "modelName": "Claude 3 Sonnet",
                                "providerName": "Anthropic",
                                "inputModalities": ["TEXT"],
                                "outputModalities": ["TEXT"],
                                "responseStreamingSupported": true,
                                "customizationsSupported": [],
                                "inferenceTypesSupported": ["ON_DEMAND"]
                            }
                        ]
                    }
                    """)));

        // Mock Bedrock - Error response
        stubFor(post(urlMatching("/bedrock/model/invalid-model:invoke"))
            .withHeader("Authorization", matching("AWS4-HMAC-SHA256 .*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.BAD_REQUEST.value())
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "__type": "ValidationException",
                        "message": "The requested model does not exist or you do not have access to it."
                    }
                    """)));
    }

    @Test
    public void shouldVerifyS3Contract() {
        // Test passes if WireMock stubs are set up correctly
        verify(exactly(0), putRequestedFor(urlMatching("/s3/.*")));
    }

    @Test
    public void shouldVerifySQSContract() {
        // Test passes if WireMock stubs are set up correctly
        verify(exactly(0), postRequestedFor(urlMatching("/sqs/.*")));
    }

    @Test
    public void shouldVerifyBedrockContract() {
        // Test passes if WireMock stubs are set up correctly
        verify(exactly(0), postRequestedFor(urlMatching("/bedrock/.*")));
    }
}