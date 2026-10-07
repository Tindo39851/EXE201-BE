package com.gametrust.backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JwtServiceTest {

    @Test
    void refreshTokensAlwaysHaveUniqueJwtIds() {
        JwtService jwtService = new JwtService(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                "gametrust.test",
                900000L,
                604800000L,
                false
        );

        String first = jwtService.generateRefreshToken("demo");
        String second = jwtService.generateRefreshToken("demo");

        assertNotEquals(first, second);
    }
}
