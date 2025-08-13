package contracts.travelplans

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return not found when travel plan doesn't exist"
    
    request {
        method GET()
        url '/api/v1/travel-plans/nonexistent'
    }
    
    response {
        status NOT_FOUND()
        headers {
            contentType(applicationJson())
        }
        body(
            success: false,
            message: "Travel plan not found",
            error: "RESOURCE_NOT_FOUND",
            data: null
        )
    }
}