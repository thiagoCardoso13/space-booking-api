package com.thiago.spacebooking.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(UUID id, UUID spaceId, UUID userId, Instant startsAt, Instant endsAt,
        BookingStatus status, Instant createdAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getSpace().getId(), booking.getUser().getId(),
                booking.getStartsAt(), booking.getEndsAt(), booking.getStatus(), booking.getCreatedAt());
    }
}
