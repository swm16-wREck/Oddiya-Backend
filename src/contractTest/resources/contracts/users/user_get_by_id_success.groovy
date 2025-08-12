package contracts.users

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should get user by ID successfully"
    
    request {
        method GET()
        url '/api/v1/users/user123'
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "User retrieved successfully",
            data: [
                id: "user123",
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