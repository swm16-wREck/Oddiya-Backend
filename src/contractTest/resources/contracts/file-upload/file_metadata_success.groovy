package contracts.fileupload

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should get file metadata successfully"
    
    request {
        method GET()
        url '/api/files/metadata/image123/profile-picture.jpg'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "File metadata retrieved successfully",
            data: [
                key: "image123/profile-picture.jpg",
                bucket: anyNonBlankString(),
                size: anyPositiveInt(),
                contentType: "image/jpeg",
                etag: anyNonBlankString(),
                metadata: [
                    category: "image",
                    "public-access": "false",
                    "original-filename": "profile-picture.jpg"
                ],
                exists: true,
                originalFilename: "profile-picture.jpg",
                extension: "jpg",
                virusScanStatus: anyOf("clean", "unknown", "scanning")
            ]
        )
        bodyMatchers {
            jsonPath('$.data.bucket', byType())
            jsonPath('$.data.size', byType())
            jsonPath('$.data.etag', byType())
            jsonPath('$.data.exists', byType())
        }
    }
}