package contracts.auth

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should validate token and return false for invalid token"
    
    request {
        method GET()
        url '/api/v1/auth/validate'
        headers {
            authorization: "Bearer invalid-jwt-token"
        }
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Token validation result",
            data: false
        )
    }
}