package com.thiago.spacebooking.booking;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** The end of the booking is derived from the fixed slot duration (R2), so only the start is sent. */
public record BookingRequest(@NotNull UUID spaceId, @NotNull Instant startsAt) {
}
