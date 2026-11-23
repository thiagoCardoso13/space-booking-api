package com.thiago.spacebooking.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** The end of the booking is derived from the fixed slot duration (R2), so only the start is sent. */
public record BookingRequest(@NotNull UUID spaceId, @Schema(example = "2030-01-16T10:00:00+01:00", description = "Must be on the full hour") @NotNull Instant startsAt) {
}
