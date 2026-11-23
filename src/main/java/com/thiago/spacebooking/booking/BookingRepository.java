package com.thiago.spacebooking.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    long countByUserIdAndStatusAndStartsAtAfter(UUID userId, BookingStatus status, Instant after);

    /** Confirmed bookings of a space that overlap the half-open interval [from, to). */
    @Query("""
            select b from Booking b
            where b.space.id = :spaceId and b.status = com.thiago.spacebooking.booking.BookingStatus.CONFIRMED
              and b.startsAt < :to and b.endsAt > :from
            """)
    List<Booking> findConfirmedOverlapping(UUID spaceId, Instant from, Instant to);
}
