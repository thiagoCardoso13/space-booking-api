package com.thiago.spacebooking.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record LocationRequest(
        @Schema(example = "Downtown Coworking") @NotBlank @Size(max = 150) String name,
        @Schema(example = "Calle Mayor 1") @NotBlank @Size(max = 255) String address,
        @Schema(example = "Elche") @NotBlank @Size(max = 100) String city,
        @Schema(example = "08:00", type = "string") @NotNull LocalTime opensAt,
        @Schema(example = "20:00", type = "string") @NotNull LocalTime closesAt) {
}
