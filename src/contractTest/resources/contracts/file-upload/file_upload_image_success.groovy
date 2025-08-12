package contracts.fileupload

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should upload image file successfully"
    
    request {
        method POST()
        url '/api/files/upload'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
        multipart(
            file: named("file", "test-image.jpg", "image/jpeg", "sample image content"),
            category: "image",
            public: "false"
        )
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "File uploaded successfully",
            data: [
                key: anyNonBlankString(),
                bucket: anyNonBlankString(),
                url: anyUrl(),
                publicUrl: anyUrl(),
                size: anyPositiveInt(),
                contentType: "image/jpeg",
                etag: anyNonBlankString(),
                success: true,
                originalFilename: "test-image.jpg",
                extension: "jpg",
                metadata: [
                    category: "image",
                    "public-access": "false"
                ]
            ]
        )
        bodyMatchers {
            jsonPath('$.data.key', byRegex('[A-Za-z0-9/_.-]+'))
            jsonPath('$.data.bucket', byType())
            jsonPath('$.data.url', byRegex('https?://.*'))
            jsonPath('$.data.publicUrl', byRegex('https?://.*'))
            jsonPath('$.data.size', byType())
            jsonPath('$.data.etag', byType())
        }
    }
}