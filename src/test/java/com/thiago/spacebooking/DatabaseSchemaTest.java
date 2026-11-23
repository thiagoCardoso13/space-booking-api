package com.thiago.spacebooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiago.spacebooking.booking.Booking;
import com.thiago.spacebooking.booking.BookingRepository;
import com.thiago.spacebooking.location.LocationRepository;
import com.thiago.spacebooking.space.Space;
import com.thiago.spacebooking.space.SpaceRepository;
import com.thiago.spacebooking.user.Role;
import com.thiago.spacebooking.user.User;
import com.thiago.spacebooking.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the Flyway migrations, the JPA mapping (Hibernate runs with ddl-auto=validate)
 * and the database-level guarantee that confirmed bookings never overlap.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DatabaseSchemaTest {

    private static final Instant NINE = Instant.parse("2030-01-15T09:00:00Z");
    private static final Instant TEN = Instant.parse("2030-01-15T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2030-01-15T11:00:00Z");

    @Autowired
    UserRepository users;
    @Autowired
    LocationRepository locations;
    @Autowired
    SpaceRepository spaces;
    @Autowired
    BookingRepository bookings;

    User admin;
    Space space;

    @BeforeEach
    void setUp() {
        admin = users.findByEmail("admin@example.com").orElseThrow();
        space = spaces.findAll().getFirst();
    }

    @Test
    void seedDataIsLoadedByMigrations() {
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(locations.count()).isEqualTo(2);
        assertThat(spaces.count()).isEqualTo(6);
    }

    @Test
    void overlappingConfirmedBookingsAreRejectedByTheDatabase() {
        bookings.saveAndFlush(new Booking(space, admin, NINE, TEN));

        assertThatThrownBy(() -> bookings.saveAndFlush(new Booking(space, admin, Instant.parse("2030-01-15T09:30:00Z"), ELEVEN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void backToBackBookingsAreAllowed() {
        bookings.saveAndFlush(new Booking(space, admin, NINE, TEN));
        bookings.saveAndFlush(new Booking(space, admin, TEN, ELEVEN));

        assertThat(bookings.count()).isEqualTo(2);
    }

    @Test
    void cancelledBookingFreesTheSlot() {
        Booking first = bookings.saveAndFlush(new Booking(space, admin, NINE, TEN));
        first.cancel();
        bookings.saveAndFlush(first);

        bookings.saveAndFlush(new Booking(space, admin, NINE, TEN));

        assertThat(bookings.count()).isEqualTo(2);
    }

    @Test
    void sameSlotInDifferentSpacesIsAllowed() {
        Space other = spaces.findAll().get(1);

        bookings.saveAndFlush(new Booking(space, admin, NINE, TEN));
        bookings.saveAndFlush(new Booking(other, admin, NINE, TEN));

        assertThat(bookings.count()).isEqualTo(2);
    }
}
