package edu.findback.common;

public record ApiError(
        int status,
        String message
) {
}