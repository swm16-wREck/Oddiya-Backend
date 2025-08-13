package contracts.auth

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should refresh access token when valid refresh token provided"
    
    request {
        method POST()
        url '/api/v1/auth/refresh'
        headers {
            contentType(applicationJson())
        }
        body(
            refreshToken: "valid-refresh-token-12345"
        )
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Token refreshed successfully",
            data: [
                accessToken: anyNonBlankString(),
                refreshToken: anyNonBlankString(),
                tokenType: "Bearer",
                expiresIn: anyPositiveInt(),
                userId: anyNonBlankString()
            ]
        )
        bodyMatchers {
            jsonPath('$.data.accessToken', byRegex('^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$'))
            jsonPath('$.data.refreshToken', byRegex('^[A-Za-z0-9-]+$'))
            jsonPath('$.data.expiresIn', byType())
            jsonPath('$.data.userId', byRegex('^[A-Za-z0-9-]+$'))
        }
    }
}