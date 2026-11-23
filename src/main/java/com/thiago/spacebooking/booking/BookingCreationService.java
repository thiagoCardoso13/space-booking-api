package com.thiago.spacebooking.booking;

import com.thiago.spacebooking.common.ConflictException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Creates bookings and retries when the database aborts the transaction because of lock contention.
 *
 * <p>When several requests insert overlapping bookings at the same time, PostgreSQL may abort one of them with
 * "deadlock detected" while it checks the exclusion constraint. The aborted transaction is lost, so the retry has to
 * happen outside of it (this class is deliberately not transactional). The new attempt runs in a fresh transaction,
 * sees the booking that won, and ends as a clean 409 instead of a server error.
 */
@Service
public class BookingCreationService {

    static final int MAX_ATTEMPTS = 4;

    private static final Logger log = LoggerFactory.getLogger(BookingCreationService.class);

    private final BookingService bookingService;

    public BookingCreationService(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public Booking create(UUID userId, BookingRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return bookingService.create(userId, request);
            } catch (PessimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Giving up on a booking after {} attempts because of lock contention", attempt);
                    throw new ConflictException("This slot is being booked by other requests right now. Please try again.");
                }
                backOff(attempt);
            }
        }
    }

    /** Short randomized pause so the competing requests do not retry in lockstep. */
    private void backOff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 25) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException("The request was interrupted.");
        }
    }
}
