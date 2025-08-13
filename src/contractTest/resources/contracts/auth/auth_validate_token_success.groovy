package contracts.auth

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should validate token and return true for valid token"
    
    request {
        method GET()
        url '/api/v1/auth/validate'
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
            message: "Token is valid",
            data: true
        )
    }
}