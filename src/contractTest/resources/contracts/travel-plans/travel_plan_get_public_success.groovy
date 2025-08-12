package contracts.travelplans

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should get public travel plans successfully"
    
    request {
        method GET()
        url '/api/v1/travel-plans/public'
        urlPath('/api/v1/travel-plans/public') {
            queryParameters {
                parameter 'page': 0
                parameter 'size': 20
                parameter 'sortBy': 'viewCount'
                parameter 'sortDirection': 'DESC'
            }
        }
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Public travel plans retrieved successfully",
            data: [
                content: [
                    [
                        id: anyNonBlankString(),
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
                ],
                page: 0,
                size: 20,
                totalElements: 1,
                totalPages: 1,
                first: true,
                last: true
            ]
        )
    }
}