package com.thiago.spacebooking.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@Schema(example = "ana@example.com") @NotBlank String email, @Schema(example = "S3cure-pass!") @NotBlank String password) {
}
