package contracts.fileupload

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should generate presigned download URL successfully"
    
    request {
        method GET()
        url '/api/files/presigned-download-url/image123/profile-picture.jpg'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
        urlPath('/api/files/presigned-download-url/image123/profile-picture.jpg') {
            queryParameters {
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
            message: "Presigned download URL generated successfully",
            data: [
                key: "image123/profile-picture.jpg",
                url: anyUrl(),
                method: "GET",
                expiresAt: anyIso8601WithOffset(),
                success: true,
                instructions: "Use GET method to download file from this URL"
            ]
        )
        bodyMatchers {
            jsonPath('$.data.url', byRegex('https?://.*'))
        }
    }
}