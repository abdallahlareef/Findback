package edu.findback.auth;

public record LoginResponse(
        Long id,
        String name,
        String email,
        String role
) {
}