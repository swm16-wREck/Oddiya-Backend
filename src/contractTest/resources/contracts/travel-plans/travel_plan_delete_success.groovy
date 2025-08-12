package contracts.travelplans

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should delete travel plan successfully"
    
    request {
        method DELETE()
        url '/api/v1/travel-plans/tp123'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
    }
    
    response {
        status NO_CONTENT()
    }
}