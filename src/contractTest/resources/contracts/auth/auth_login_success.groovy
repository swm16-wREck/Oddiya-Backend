package contracts.auth

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return access token when valid OAuth login credentials provided"
    
    request {
        method POST()
        url '/api/v1/auth/login'
        headers {
            contentType(applicationJson())
        }
        body(
            provider: "GOOGLE",
            idToken: "valid-google-id-token",
            deviceType: "MOBILE",
            deviceToken: "device123"
        )
    }
    
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            success: true,
            message: "Login successful",
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