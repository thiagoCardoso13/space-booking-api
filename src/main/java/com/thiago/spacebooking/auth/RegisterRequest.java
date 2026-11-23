package com.thiago.spacebooking.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Ana García") @NotBlank @Size(max = 100) String name,
        @Schema(example = "ana@example.com") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "S3cure-pass!") @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password) {
}
