package com.thiago.spacebooking.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record LocationRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 100) String city,
        @NotNull LocalTime opensAt,
        @NotNull LocalTime closesAt) {
}
