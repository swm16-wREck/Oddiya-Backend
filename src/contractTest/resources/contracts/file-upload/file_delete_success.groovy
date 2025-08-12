package contracts.fileupload

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should delete file successfully"
    
    request {
        method DELETE()
        url '/api/files/image123/profile-picture.jpg'
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
            message: "File deleted successfully",
            data: [
                key: "image123/profile-picture.jpg",
                deleted: true,
                message: "File deleted successfully"
            ]
        )
    }
}