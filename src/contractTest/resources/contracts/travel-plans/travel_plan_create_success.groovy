package contracts.travelplans

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should create travel plan successfully with valid request"
    
    request {
        method POST()
        url '/api/v1/travel-plans'
        headers {
            contentType(applicationJson())
            authorization: "Bearer valid-jwt-token"
        }
        body(
            title: "Seoul 3-Day Adventure",
            description: "Explore the best of Seoul in 3 days",
            startDate: "2024-06-01T09:00:00",
            endDate: "2024-06-03T18:00:00",
            isPublic: true,
            tags: ["seoul", "culture", "food"]
        )
    }
    
    response {
        status CREATED()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Travel plan created successfully",
            data: [
                id: anyNonBlankString(),
                title: "Seoul 3-Day Adventure",
                description: "Explore the best of Seoul in 3 days",
                userId: anyNonBlankString(),
                userName: anyNonBlankString(),
                status: "DRAFT",
                startDate: anyIso8601WithOffset(),
                endDate: anyIso8601WithOffset(),
                isPublic: true,
                viewCount: 0,
                likeCount: 0,
                tags: ["seoul", "culture", "food"],
                createdAt: anyIso8601WithOffset(),
                updatedAt: anyIso8601WithOffset()
            ]
        )
        bodyMatchers {
            jsonPath('$.data.id', byRegex('[A-Za-z0-9-]+'))
            jsonPath('$.data.userId', byRegex('[A-Za-z0-9-]+'))
            jsonPath('$.data.userName', byType())
            jsonPath('$.data.viewCount', byType())
            jsonPath('$.data.likeCount', byType())
        }
    }
}