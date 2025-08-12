package contracts.places

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should create place successfully with valid request"
    
    request {
        method POST()
        url '/api/v1/places'
        headers {
            contentType(applicationJson())
            authorization: "Bearer valid-jwt-token"
        }
        body(
            name: "Gyeongbokgung Palace",
            description: "A historic palace in Seoul, South Korea",
            category: "attraction",
            address: "161 Sajik-ro, Jongno-gu, Seoul, South Korea",
            latitude: 37.5788,
            longitude: 126.9770,
            imageUrls: ["https://example.com/gyeongbok1.jpg"],
            tags: ["palace", "history", "culture"]
        )
    }
    
    response {
        status CREATED()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Place created successfully",
            data: [
                id: anyNonBlankString(),
                name: "Gyeongbokgung Palace",
                description: "A historic palace in Seoul, South Korea",
                category: "attraction",
                address: "161 Sajik-ro, Jongno-gu, Seoul, South Korea",
                latitude: 37.5788,
                longitude: 126.9770,
                rating: anyDouble(),
                viewCount: 0,
                imageUrls: ["https://example.com/gyeongbok1.jpg"],
                tags: ["palace", "history", "culture"],
                createdAt: anyIso8601WithOffset(),
                updatedAt: anyIso8601WithOffset()
            ]
        )
        bodyMatchers {
            jsonPath('$.data.id', byRegex('[A-Za-z0-9-]+'))
            jsonPath('$.data.rating', byType())
            jsonPath('$.data.viewCount', byType())
        }
    }
}