package contracts.users

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should update user profile successfully"
    
    request {
        method PUT()
        url '/api/v1/users/profile'
        headers {
            contentType(applicationJson())
            authorization: "Bearer valid-jwt-token"
        }
        body(
            nickname: "Johnny",
            bio: "Updated bio: Love exploring new places!",
            profileImageUrl: "https://example.com/new-profile.jpg"
        )
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "User profile updated successfully",
            data: [
                id: anyNonBlankString(),
                email: "john.doe@example.com",
                username: "johndoe",
                nickname: "Johnny",
                bio: "Updated bio: Love exploring new places!",
                profileImageUrl: "https://example.com/new-profile.jpg",
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
        }
    }
}