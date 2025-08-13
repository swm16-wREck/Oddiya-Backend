package contracts.auth

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return error when invalid refresh token provided"
    
    request {
        method POST()
        url '/api/v1/auth/refresh'
        headers {
            contentType(applicationJson())
        }
        body(
            refreshToken: "invalid-refresh-token"
        )
    }
    
    response {
        status UNAUTHORIZED()
        headers {
            contentType(applicationJson())
        }
        body(
            success: false,
            message: "Invalid refresh token",
            error: "INVALID_REFRESH_TOKEN",
            data: null
        )
    }
}