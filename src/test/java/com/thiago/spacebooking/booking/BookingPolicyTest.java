package com.thiago.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiago.spacebooking.location.Location;
import com.thiago.spacebooking.space.Space;
import com.thiago.spacebooking.space.SpaceType;
import com.thiago.spacebooking.user.Role;
import com.thiago.spacebooking.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the booking rules. "Now" is fixed at 2030-01-15 09:00 in Madrid (08:00 UTC, winter time).
 * The location is open 09:00-21:00 Madrid time.
 */
class BookingPolicyTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2030-01-15T08:00:00Z");

    private final BookingProperties properties =
            new BookingProperties(MADRID, Duration.ofMinutes(60), 14, 3, Duration.ofHours(2));
    private final BookingPolicy policy = new BookingPolicy(Clock.fixed(NOW, ZoneOffset.UTC), properties);

    private final Location location = new Location("L", "Street", "Elche", LocalTime.of(9, 0), LocalTime.of(21, 0));
    private final Space space = new Space(location, "Court 1", SpaceType.SPORTS_COURT);

    /** Madrid local time on 2030-01-16 (tomorrow) as an instant. */
    private static Instant tomorrowAt(int hour, int minute) {
        return java.time.ZonedDateTime.of(2030, 1, 16, hour, minute, 0, 0, MADRID).toInstant();
    }

    private String rule(Optional<BookingPolicy.Violation> violation) {
        return violation.map(BookingPolicy.Violation::rule).orElse("OK");
    }

    @Test
    void acceptsAValidSlot() {
        assertThat(rule(policy.checkSlot(space, tomorrowAt(10, 0)))).isEqualTo("OK");
    }

    @Test
    void r1_rejectsSlotsOutsideOpeningHours() {
        assertThat(rule(policy.checkSlot(space, tomorrowAt(8, 0)))).isEqualTo("R1");   // before opening
        assertThat(rule(policy.checkSlot(space, tomorrowAt(21, 0)))).isEqualTo("R1");  // starts at closing time
        assertThat(rule(policy.checkSlot(space, tomorrowAt(23, 0)))).isEqualTo("R1");  // crosses midnight
    }

    @Test
    void r1_acceptsTheFirstAndLastSlotOfTheDay() {
        assertThat(rule(policy.checkSlot(space, tomorrowAt(9, 0)))).isEqualTo("OK");   // opens at 09:00
        assertThat(rule(policy.checkSlot(space, tomorrowAt(20, 0)))).isEqualTo("OK");  // ends exactly at 21:00
    }

    @Test
    void r1_usesTheLocalTimeZoneNotUtc() {
        // 20:00 UTC looks inside 09:00-21:00 if read as UTC, but it is 21:00 in Madrid: the closing time.
        assertThat(rule(policy.checkSlot(space, Instant.parse("2030-01-16T20:00:00Z")))).isEqualTo("R1");
        // 08:00 UTC is 09:00 in Madrid: the opening time.
        assertThat(rule(policy.checkSlot(space, Instant.parse("2030-01-16T08:00:00Z")))).isEqualTo("OK");
    }

    @Test
    void r1_followsDaylightSavingTime() {
        // In summer Madrid is UTC+2. With "now" in July, 19:00 UTC is 21:00 Madrid (closed) and 18:00 UTC is 20:00 (open).
        BookingPolicy summer = new BookingPolicy(Clock.fixed(Instant.parse("2030-07-01T08:00:00Z"), ZoneOffset.UTC), properties);
        assertThat(rule(summer.checkSlot(space, Instant.parse("2030-07-02T19:00:00Z")))).isEqualTo("R1");
        assertThat(rule(summer.checkSlot(space, Instant.parse("2030-07-02T18:00:00Z")))).isEqualTo("OK");
    }

    @Test
    void r3_rejectsStartsThatAreNotOnTheFullHour() {
        assertThat(rule(policy.checkSlot(space, tomorrowAt(10, 30)))).isEqualTo("R3");
        assertThat(rule(policy.checkSlot(space, tomorrowAt(10, 0).plusSeconds(1)))).isEqualTo("R3");
    }

    @Test
    void r5_rejectsPastAndCurrentInstants() {
        assertThat(rule(policy.checkSlot(space, Instant.parse("2030-01-15T07:00:00Z")))).isEqualTo("R5"); // 08:00 Madrid, past
        assertThat(rule(policy.checkSlot(space, NOW))).isEqualTo("R5");                                 // now is not in the future
    }

    @Test
    void r6_rejectsBookingsBeyondTheAdvanceWindow() {
        Instant inside = java.time.ZonedDateTime.of(2030, 1, 29, 9, 0, 0, 0, MADRID).toInstant();   // exactly 14 days ahead
        Instant beyond = java.time.ZonedDateTime.of(2030, 1, 29, 10, 0, 0, 0, MADRID).toInstant();  // 14 days + 1 hour
        assertThat(rule(policy.checkSlot(space, inside))).isEqualTo("OK");
        assertThat(rule(policy.checkSlot(space, beyond))).isEqualTo("R6");
    }

    @Test
    void r10_rejectsInactiveSpacesBeforeAnythingElse() {
        space.setActive(false);
        assertThat(rule(policy.checkSlot(space, tomorrowAt(10, 0)))).isEqualTo("R10");
        assertThat(rule(policy.checkSlot(space, tomorrowAt(10, 30)))).isEqualTo("R10");
    }

    @Test
    void r7_limitsActiveFutureBookingsPerUser() {
        assertThat(rule(policy.checkUserLimit(2))).isEqualTo("OK");
        assertThat(rule(policy.checkUserLimit(3))).isEqualTo("R7");
        assertThat(rule(policy.checkUserLimit(10))).isEqualTo("R7");
    }

    @Test
    void r9_ownersNeedTwoHoursNoticeToCancel() {
        User owner = new User("O", "o@example.com", "hash", Role.USER);
        Booking in3Hours = new Booking(space, owner, NOW.plus(Duration.ofHours(3)), NOW.plus(Duration.ofHours(4)));
        Booking in2Hours = new Booking(space, owner, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
        Booking in119Min = new Booking(space, owner, NOW.plus(Duration.ofMinutes(119)), NOW.plus(Duration.ofMinutes(179)));
        Booking started = new Booking(space, owner, NOW.minus(Duration.ofHours(1)), NOW);

        assertThat(rule(policy.checkCancellation(in3Hours, false))).isEqualTo("OK");
        assertThat(rule(policy.checkCancellation(in2Hours, false))).isEqualTo("OK");   // exactly 2 h is allowed
        assertThat(rule(policy.checkCancellation(in119Min, false))).isEqualTo("R9");
        assertThat(rule(policy.checkCancellation(started, false))).isEqualTo("R9");
    }

    @Test
    void r9_adminsCanCancelAtAnyTime() {
        User owner = new User("O", "o@example.com", "hash", Role.USER);
        Booking started = new Booking(space, owner, NOW.minus(Duration.ofHours(1)), NOW);
        assertThat(rule(policy.checkCancellation(started, true))).isEqualTo("OK");
    }

    @Test
    void r2_endIsStartPlusTheFixedSlotDuration() {
        assertThat(policy.endOf(tomorrowAt(10, 0))).isEqualTo(tomorrowAt(11, 0));
    }
}
