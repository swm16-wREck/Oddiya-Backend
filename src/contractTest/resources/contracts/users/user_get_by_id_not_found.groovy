package contracts.users

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return not found when user doesn't exist"
    
    request {
        method GET()
        url '/api/v1/users/nonexistent'
    }
    
    response {
        status NOT_FOUND()
    }
}