package com.careerconnect.auth.dto;

import java.time.Instant;

public class AuthResponse {

    private String accessToken;
    private String tokenType = "Bearer";
    private Instant expiresAt;
    private AuthUserDto user;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String tokenType, Instant expiresAt, AuthUserDto user) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    public static AuthResponse of(String accessToken, Instant expiresAt, AuthUserDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresAt, user);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public AuthUserDto getUser() {
        return user;
    }

    public void setUser(AuthUserDto user) {
        this.user = user;
    }
}
