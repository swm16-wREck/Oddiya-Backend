package contracts.users

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should update user preferences successfully"
    
    request {
        method PUT()
        url '/api/v1/users/preferences'
        headers {
            contentType(applicationJson())
            authorization: "Bearer valid-jwt-token"
        }
        body(
            language: "en",
            currency: "USD",
            timezone: "UTC",
            notifications: "enabled"
        )
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "User preferences updated successfully",
            data: [
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
        )
    }
}