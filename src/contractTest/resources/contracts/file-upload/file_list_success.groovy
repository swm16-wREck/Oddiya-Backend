package contracts.fileupload

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should list files successfully"
    
    request {
        method GET()
        url '/api/files/list'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
        urlPath('/api/files/list') {
            queryParameters {
                parameter 'prefix': 'user-uploads/'
                parameter 'maxResults': 100
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
            message: "Files listed successfully",
            data: [
                "user-uploads/image123/profile-picture.jpg",
                "user-uploads/documents/resume.pdf",
                "user-uploads/videos/travel-vlog.mp4"
            ]
        )
    }
}