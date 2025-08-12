package contracts.auth

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return error when invalid OAuth token provided"
    
    request {
        method POST()
        url '/api/v1/auth/login'
        headers {
            contentType(applicationJson())
        }
        body(
            provider: "GOOGLE",
            idToken: "invalid-token",
            deviceType: "MOBILE"
        )
    }
    
    response {
        status BAD_REQUEST()
        headers {
            contentType(applicationJson())
        }
        body(
            success: false,
            message: "Invalid OAuth token",
            error: "INVALID_TOKEN",
            data: null
        )
    }
}