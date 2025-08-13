package contracts.users

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should get current user profile successfully"
    
    request {
        method GET()
        url '/api/v1/users/profile'
        headers {
            authorization: "Bearer valid-jwt-token"
        }
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "User profile retrieved successfully",
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
        bodyMatchers {
            jsonPath('$.data.id', byRegex('[A-Za-z0-9-]+'))
            jsonPath('$.data.email', byRegex('[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}'))
            jsonPath('$.data.isEmailVerified', byType())
            jsonPath('$.data.isPremium', byType())
            jsonPath('$.data.isActive', byType())
        }
    }
}