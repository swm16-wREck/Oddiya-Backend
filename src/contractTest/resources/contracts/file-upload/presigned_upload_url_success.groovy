package contracts.fileupload

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should generate presigned upload URL successfully"
    
    request {
        method POST()
        url '/api/files/presigned-upload-url'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
        urlPath('/api/files/presigned-upload-url') {
            queryParameters {
                parameter 'filename': 'profile-picture.jpg'
                parameter 'contentType': 'image/jpeg'
                parameter 'category': 'image'
                parameter 'expirationSeconds': 3600
            }
        }
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Presigned URL generated successfully",
            data: [
                key: anyNonBlankString(),
                url: anyUrl(),
                method: "PUT",
                expiresAt: anyIso8601WithOffset(),
                contentType: "image/jpeg",
                maxFileSize: anyPositiveInt(),
                success: true,
                instructions: "Use PUT method to upload file to this URL"
            ]
        )
        bodyMatchers {
            jsonPath('$.data.key', byRegex('[A-Za-z0-9/_.-]+'))
            jsonPath('$.data.url', byRegex('https?://.*'))
            jsonPath('$.data.maxFileSize', byType())
        }
    }
}