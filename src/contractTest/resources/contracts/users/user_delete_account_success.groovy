package contracts.users

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should delete user account successfully"
    
    request {
        method DELETE()
        url '/api/v1/users/profile'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
    }
    
    response {
        status NO_CONTENT()
    }
}