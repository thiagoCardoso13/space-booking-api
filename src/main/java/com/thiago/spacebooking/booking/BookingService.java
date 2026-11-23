package com.thiago.spacebooking.booking;

import com.thiago.spacebooking.common.BusinessRuleException;
import com.thiago.spacebooking.common.ConflictException;
import com.thiago.spacebooking.common.CurrentUser;
import com.thiago.spacebooking.common.ResourceNotFoundException;
import com.thiago.spacebooking.space.Space;
import com.thiago.spacebooking.space.SpaceRepository;
import com.thiago.spacebooking.user.User;
import com.thiago.spacebooking.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingService {

    private static final String OVERLAP_CONSTRAINT = "no_overlapping_bookings";

    private final BookingRepository bookings;
    private final SpaceRepository spaces;
    private final UserRepository users;
    private final BookingPolicy policy;
    private final BookingProperties properties;
    private final Clock clock;

    public BookingService(BookingRepository bookings, SpaceRepository spaces, UserRepository users,
            BookingPolicy policy, BookingProperties properties, Clock clock) {
        this.bookings = bookings;
        this.spaces = spaces;
        this.users = users;
        this.policy = policy;
        this.properties = properties;
        this.clock = clock;
    }

    public Booking create(UUID userId, BookingRequest request) {
        Space space = spaces.findById(request.spaceId()).orElseThrow(() -> new ResourceNotFoundException("Space not found."));
        policy.checkSlot(space, request.startsAt()).ifPresent(BookingService::reject);

        // The user row lock serializes this user's concurrent bookings, so the R7 limit cannot be exceeded
        // by parallel requests (count + insert would otherwise race).
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        long active = bookings.countByUserIdAndStatusAndStartsAtAfter(userId, BookingStatus.CONFIRMED, clock.instant());
        policy.checkUserLimit(active).ifPresent(BookingService::reject);

        Booking booking = new Booking(space, user, request.startsAt(), policy.endOf(request.startsAt()));
        try {
            return bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            // R4: the exclusion constraint is the authority on overlaps, even for simultaneous requests.
            if (mentionsConstraint(e)) {
                throw new ConflictException("This slot is already booked.");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<Booking> listMine(UUID userId, BookingStatus status, Instant from, Instant to, Pageable pageable) {
        return bookings.findAll(filter(userId, null, status, from, to), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Booking> listForAdmin(UUID spaceId, LocalDate date, Pageable pageable) {
        Instant from = date == null ? null : date.atStartOfDay(properties.zone()).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(properties.zone()).toInstant();
        return bookings.findAll(filter(null, spaceId, null, from, to), pageable);
    }

    @Transactional(readOnly = true)
    public Booking get(UUID id, CurrentUser caller) {
        return visibleTo(id, caller);
    }

    /** Cancelling an already cancelled booking is a no-op (idempotent). */
    public void cancel(UUID id, CurrentUser caller) {
        Booking booking = visibleTo(id, caller);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }
        policy.checkCancellation(booking, caller.admin()).ifPresent(BookingService::reject);
        booking.cancel();
    }

    /** R8: only the owner or an admin. Others get 404 so the existence of a booking is not leaked. */
    private Booking visibleTo(UUID id, CurrentUser caller) {
        Booking booking = bookings.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found."));
        if (!caller.admin() && !booking.getUser().getId().equals(caller.id())) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        return booking;
    }

    private static Specification<Booking> filter(UUID userId, UUID spaceId, BookingStatus status, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (spaceId != null) {
                predicates.add(cb.equal(root.get("space").get("id"), spaceId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startsAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void reject(BookingPolicy.Violation violation) {
        throw new BusinessRuleException(violation.rule(), violation.message());
    }

    private static boolean mentionsConstraint(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(OVERLAP_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
