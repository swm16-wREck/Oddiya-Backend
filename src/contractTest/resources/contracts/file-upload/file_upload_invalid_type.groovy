package contracts.fileupload

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should reject invalid file type for image category"
    
    request {
        method POST()
        url '/api/files/upload'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
        multipart(
            file: named("file", "test-document.txt", "text/plain", "sample text content"),
            category: "image",
            public: "false"
        )
    }
    
    response {
        status BAD_REQUEST()
        headers {
            contentType(applicationJson())
        }
        body(
            success: false,
            message: anyNonBlankString(),
            error: "FILE_VALIDATION_FAILED",
            data: null
        )
    }
}