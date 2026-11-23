package com.thiago.spacebooking.booking;

import com.thiago.spacebooking.location.Location;
import com.thiago.spacebooking.space.Space;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The business rules for bookings, isolated from persistence so each one is easy to unit test.
 *
 * <p>R4 (no overlap in the same space) is deliberately NOT here: it is enforced by an exclusion constraint
 * in the database, the only place that can guarantee it under concurrent requests.
 */
@Component
public class BookingPolicy {

    /** A broken rule: a stable code (R1..R10) and a human readable message. */
    public record Violation(String rule, String message) {
    }

    private final Clock clock;
    private final BookingProperties properties;

    public BookingPolicy(Clock clock, BookingProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public Instant endOf(Instant startsAt) {
        return startsAt.plus(properties.slotDuration());
    }

    /**
     * Checks everything that depends only on the slot and the space: R10, R3, R5, R6 and R1.
     * Empty means the slot can be booked (R7, the per-user limit, needs the user's bookings and is checked apart).
     */
    public Optional<Violation> checkSlot(Space space, Instant startsAt) {
        if (!space.isActive()) {
            return violation("R10", "This space is inactive and cannot be booked.");
        }
        ZonedDateTime start = startsAt.atZone(properties.zone());
        if (!start.truncatedTo(ChronoUnit.HOURS).equals(start)) {
            return violation("R3", "Bookings must start on the full hour.");
        }
        Instant now = clock.instant();
        if (!startsAt.isAfter(now)) {
            return violation("R5", "Bookings cannot be made in the past.");
        }
        if (startsAt.isAfter(now.plus(Duration.ofDays(properties.maxAdvanceDays())))) {
            return violation("R6", "Bookings can be made at most " + properties.maxAdvanceDays() + " days in advance.");
        }
        Location location = space.getLocation();
        ZonedDateTime end = start.plus(properties.slotDuration());
        boolean sameDay = end.toLocalDate().equals(start.toLocalDate());
        if (!sameDay || start.toLocalTime().isBefore(location.getOpensAt())
                || end.toLocalTime().isAfter(location.getClosesAt())) {
            return violation("R1", "The booking is outside the opening hours of the location ("
                    + location.getOpensAt() + "-" + location.getClosesAt() + ").");
        }
        return Optional.empty();
    }

    /** R7: limit of active future bookings per user. */
    public Optional<Violation> checkUserLimit(long activeFutureBookings) {
        if (activeFutureBookings >= properties.maxActiveBookingsPerUser()) {
            return violation("R7", "You already have " + properties.maxActiveBookingsPerUser()
                    + " active future bookings.");
        }
        return Optional.empty();
    }

    /** R9: owners need minimum notice to cancel. Admins can cancel at any time. */
    public Optional<Violation> checkCancellation(Booking booking, boolean admin) {
        if (admin) {
            return Optional.empty();
        }
        Instant latest = clock.instant().plus(properties.minCancelNotice());
        if (booking.getStartsAt().isBefore(latest)) {
            return violation("R9", "Bookings can only be cancelled at least "
                    + properties.minCancelNotice().toHours() + " hours before they start.");
        }
        return Optional.empty();
    }

    private static Optional<Violation> violation(String rule, String message) {
        return Optional.of(new Violation(rule, message));
    }
}
