package pl.radarpracy.common;

import java.util.List;

/** Jednolity format błędu API: {@code {"error": "...", "message": "...", "details": [...]}}. */
public record ApiError(String error, String message, List<String> details) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, List.of());
    }
}
