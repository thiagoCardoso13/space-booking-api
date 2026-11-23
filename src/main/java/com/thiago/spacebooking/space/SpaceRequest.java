package com.thiago.spacebooking.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Used to create a space. {@code locationId} is required on creation and ignored on update. */
public record SpaceRequest(
        UUID locationId,
        @NotBlank @Size(max = 150) String name,
        @NotNull SpaceType type,
        Boolean active) {
}
