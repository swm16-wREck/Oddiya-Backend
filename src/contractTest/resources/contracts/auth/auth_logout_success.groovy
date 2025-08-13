package contracts.auth

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should successfully logout user with valid token"
    
    request {
        method POST()
        url '/api/v1/auth/logout'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
    }
    
    response {
        status NO_CONTENT()
    }
}