package contracts.travelplans

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should get travel plan by ID successfully"
    
    request {
        method GET()
        url '/api/v1/travel-plans/tp123'
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Travel plan retrieved successfully",
            data: [
                id: "tp123",
                title: "Seoul 3-Day Adventure",
                description: "Explore the best of Seoul in 3 days",
                userId: anyNonBlankString(),
                userName: anyNonBlankString(),
                status: "PUBLISHED",
                startDate: anyIso8601WithOffset(),
                endDate: anyIso8601WithOffset(),
                isPublic: true,
                viewCount: anyPositiveInt(),
                likeCount: anyPositiveInt(),
                tags: ["seoul", "culture", "food"],
                createdAt: anyIso8601WithOffset(),
                updatedAt: anyIso8601WithOffset()
            ]
        )
        bodyMatchers {
            jsonPath('$.data.userId', byRegex('[A-Za-z0-9-]+'))
            jsonPath('$.data.userName', byType())
            jsonPath('$.data.viewCount', byType())
            jsonPath('$.data.likeCount', byType())
        }
    }
}