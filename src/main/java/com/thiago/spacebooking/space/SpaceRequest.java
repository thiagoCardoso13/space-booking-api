package com.thiago.spacebooking.space;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Used to create a space. {@code locationId} is required on creation and ignored on update. */
public record SpaceRequest(
        @Schema(description = "Required when creating; ignored when updating") UUID locationId,
        @Schema(example = "Meeting Room A") @NotBlank @Size(max = 150) String name,
        @NotNull SpaceType type,
        @Schema(description = "Optional. Defaults to true on creation and keeps the current value on update") Boolean active) {
}
