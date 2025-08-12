package contracts.users

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should search users successfully"
    
    request {
        method GET()
        url '/api/v1/users/search'
        urlPath('/api/v1/users/search') {
            queryParameters {
                parameter 'q': 'john'
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
            message: "Users search completed successfully",
            data: [
                [
                    id: anyNonBlankString(),
                    email: "john.doe@example.com",
                    username: "johndoe",
                    nickname: "John",
                    bio: "Travel enthusiast and food lover",
                    profileImageUrl: "https://example.com/profile.jpg",
                    isEmailVerified: true,
                    isPremium: false,
                    isActive: true,
                    createdAt: anyIso8601WithOffset(),
                    updatedAt: anyIso8601WithOffset()
                ]
            ]
        )
    }
}