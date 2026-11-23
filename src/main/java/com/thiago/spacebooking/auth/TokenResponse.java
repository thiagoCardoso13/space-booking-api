package com.thiago.spacebooking.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
