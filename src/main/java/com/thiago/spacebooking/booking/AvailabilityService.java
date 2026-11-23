package com.thiago.spacebooking.booking;

import com.thiago.spacebooking.common.ResourceNotFoundException;
import com.thiago.spacebooking.location.Location;
import com.thiago.spacebooking.space.Space;
import com.thiago.spacebooking.space.SpaceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    public record Slot(Instant startsAt, Instant endsAt, boolean available) {
    }

    private final SpaceRepository spaces;
    private final BookingRepository bookings;
    private final BookingPolicy policy;
    private final BookingProperties properties;

    public AvailabilityService(SpaceRepository spaces, BookingRepository bookings, BookingPolicy policy,
            BookingProperties properties) {
        this.spaces = spaces;
        this.bookings = bookings;
        this.policy = policy;
        this.properties = properties;
    }

    /**
     * Every slot of the day inside the opening hours. A slot is available when the booking rules accept it
     * (not in the past, inside the advance window, active space) and nobody holds it already.
     */
    public List<Slot> forDay(UUID spaceId, LocalDate date) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException("Space not found."));
        Location location = space.getLocation();
        Instant dayStart = date.atStartOfDay(properties.zone()).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(properties.zone()).toInstant();
        List<Booking> taken = bookings.findConfirmedOverlapping(spaceId, dayStart, dayEnd);

        List<Slot> slots = new ArrayList<>();
        ZonedDateTime start = date.atTime(location.getOpensAt()).atZone(properties.zone()).truncatedTo(ChronoUnit.HOURS);
        if (start.toLocalTime().isBefore(location.getOpensAt())) {
            start = start.plusHours(1);
        }
        while (true) {
            Instant slotStart = start.toInstant();
            Instant slotEnd = policy.endOf(slotStart);
            ZonedDateTime end = slotEnd.atZone(properties.zone());
            if (!end.toLocalDate().equals(date) || end.toLocalTime().isAfter(location.getClosesAt())) {
                break;
            }
            boolean overlaps = taken.stream().anyMatch(b -> b.getStartsAt().isBefore(slotEnd) && b.getEndsAt().isAfter(slotStart));
            boolean bookable = policy.checkSlot(space, slotStart).isEmpty();
            slots.add(new Slot(slotStart, slotEnd, bookable && !overlaps));
            start = start.plus(properties.slotDuration());
        }
        return slots;
    }
}
