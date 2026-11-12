package edu.findback.auth;

public record RegisterResponse(
        Long id,
        String name,
        String email,
        String role
) {
}