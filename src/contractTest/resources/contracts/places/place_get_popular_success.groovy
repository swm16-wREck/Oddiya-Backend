package contracts.places

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should get popular places successfully"
    
    request {
        method GET()
        url '/api/v1/places/popular'
        urlPath('/api/v1/places/popular') {
            queryParameters {
                parameter 'page': 0
                parameter 'size': 20
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
            message: "Popular places retrieved successfully",
            data: [
                content: [
                    [
                        id: anyNonBlankString(),
                        name: "Gyeongbokgung Palace",
                        description: "A historic palace in Seoul, South Korea",
                        category: "attraction",
                        address: "161 Sajik-ro, Jongno-gu, Seoul, South Korea",
                        latitude: 37.5788,
                        longitude: 126.9770,
                        rating: 4.5,
                        viewCount: anyPositiveInt(),
                        imageUrls: ["https://example.com/gyeongbok1.jpg"],
                        tags: ["palace", "history", "culture"],
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