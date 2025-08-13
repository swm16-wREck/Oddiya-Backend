package contracts.travelplans

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should update travel plan successfully with valid request"
    
    request {
        method PUT()
        url '/api/v1/travel-plans/tp123'
        headers {
            contentType(applicationJson())
            authorization: "Bearer valid-jwt-token"
        }
        body(
            title: "Seoul 4-Day Extended Adventure",
            description: "Extended Seoul experience with more attractions",
            startDate: "2024-06-01T09:00:00",
            endDate: "2024-06-04T18:00:00",
            isPublic: false,
            tags: ["seoul", "culture", "food", "shopping"]
        )
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Travel plan updated successfully",
            data: [
                id: "tp123",
                title: "Seoul 4-Day Extended Adventure",
                description: "Extended Seoul experience with more attractions",
                userId: anyNonBlankString(),
                userName: anyNonBlankString(),
                status: "PUBLISHED",
                startDate: anyIso8601WithOffset(),
                endDate: anyIso8601WithOffset(),
                isPublic: false,
                viewCount: anyPositiveInt(),
                likeCount: anyPositiveInt(),
                tags: ["seoul", "culture", "food", "shopping"],
                createdAt: anyIso8601WithOffset(),
                updatedAt: anyIso8601WithOffset()
            ]
        )
    }
}